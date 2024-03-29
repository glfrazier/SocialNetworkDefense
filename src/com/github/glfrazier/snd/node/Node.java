package com.github.glfrazier.snd.node;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;
import static java.util.logging.Level.FINE;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventProcessor;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.protocol.IntroducerProtocol;
import com.github.glfrazier.snd.protocol.IntroductionProtocol;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.Pedigree;
import com.github.glfrazier.snd.protocol.RequesterProtocol;
import com.github.glfrazier.snd.protocol.SNDPMessageTransmissionProtocol;
import com.github.glfrazier.snd.protocol.TargetProtocol;
import com.github.glfrazier.snd.protocol.message.AckMessage;
import com.github.glfrazier.snd.protocol.message.AddIntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.FeedbackMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionOfferMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.protocol.message.SNDPMessage;
import com.github.glfrazier.snd.util.AddressUtils.AddressPair;
import com.github.glfrazier.snd.util.DiscoveryService.Query;
import com.github.glfrazier.snd.util.Implementation;
import com.github.glfrazier.snd.util.PropertyParser;
import com.github.glfrazier.statemachine.StateMachine;

/**
 * A node in the Social Network Defense network. Implements the SND protocol for
 * establishing VPNs between trusted entities.
 */
public class Node implements EventProcessor, MessageReceiver {

	/**
	 * This should not be a constant, per se. It should be a configurable item.
	 */
	public static final long TRANSMISSION_LATENCY = 10;

	private final InetAddress address;

	/**
	 * How the nodes that have been introduced to this node got here.
	 */
	private final Map<InetAddress, Pedigree> pedigrees = new HashMap<>();

	/**
	 * The keys of this map are the pair (requester, destination) for introduction
	 * offers that this node is waiting to send feedback about. The value for each
	 * key is the timestamp (in milliseconds) when the introduction request was
	 * accepted and the IntroductionRequest. After a suitable timeout (24 hrs?), the
	 * entry is discarded.
	 */
	private Map<AddressPair, IntroductionRequest> pendingFeedbacksToSend[];

	/**
	 * The keys of this Map are the introduction requests that we have offered and
	 * had accepted, but not yet received feedback about. The values are a duple:
	 * the introduction requests by which we know the requester and the timestamp of
	 * the introduction. If the requester is known to this node via a long-lived
	 * VPN, then the value is null. We use this map to figure out where to forward
	 * feedback to (i.e., who introduced the subject of the feedback to us). The
	 * timestamp is used to clean out old entries (see
	 * {@link #FEEDBACK_EXPIRATION_TIME}).
	 */
	private Map<IntroductionRequest, IntroductionRequest> pendingFeedbacksToReceive[];

	private int currentPendingFeedbacks = 0;

	/**
	 * The nodes to which this node has VPN connections that existed before any
	 * introductions.
	 */
	private Set<InetAddress> aprioriNeighbors;

	/**
	 * The keys are the neighbors to which this node has been introduced. The value
	 * for each key is the introduction requests that are "using" the link. When the
	 * last IntroductionRequest has been removed from the set, the VPN can be
	 * closed.
	 */
	private Map<InetAddress, Set<IntroductionRequest>> introducedNeighbors;

	public final ReputationModule reputationModule;
	protected final EventingSystem eventingSystem;
	protected final Implementation implementation;
	protected final Properties properties;

	protected boolean verbose;

	protected static final Event NODE_MAINTENANCE_EVENT = new Event() {
		private final String name = "Node Maintenance Event";

		@Override
		public String toString() {
			return name;
		}
	};

	/**
	 * The maximum time lapse between an introduction and feedback regarding that
	 * introduction.
	 */
	// TODO These constants should all be set by properties!
	protected static final long FEEDBACK_EXPIRATION_TIME = 60 * 1000; // one minute// 8 * 60 *

	private static final long MAINTENANCE_INTERVAL = FEEDBACK_EXPIRATION_TIME / 100;
	private static final int MAX_INTRODUCED_NEIGHBORS = 100;

	private static final Logger LOGGER = Logger.getLogger(Node.class.getName());

	protected Logger logger;

	private Map<Long, SNDPMessageTransmissionProtocol> ackWaiters = Collections.synchronizedMap(new HashMap<>());

	private Map<IntroductionRequest, IntroductionProtocol> registeredProtocols = Collections
			.synchronizedMap(new HashMap<>());
	@SuppressWarnings("serial")
	private Map<IntroductionRequest, String> recentlyUnregisteredProtocols = Collections
			.synchronizedMap(new LinkedHashMap<>() {
				private static final int MAX_ENTRIES = 100;

				protected boolean removeEldestEntry(Map.Entry<IntroductionRequest, String> eldest) {
					return size() > MAX_ENTRIES;
				}
			});

	private Long verboseOnIntroductionRequest;

	/**
	 * This method pulls the initialization of the pending feedbacks fields out of
	 * the body of the constructor and puts them into their own method. The purpose
	 * is to isolate the @SuppressWarnings directive to just these assignments;
	 * without this method, one is forced to suppress warnings for the entire
	 * constructor. Given that this constructor will be invoked exactly once in the
	 * lifetime of a given network node, we are not concerned about performance. (Of
	 * course, one cannot avoid awareness of the JIT compiler's inclination to
	 * in-line methods such as this, which renders concern over an unnecessary
	 * method invocation moot.)
	 */
	@SuppressWarnings("unchecked")
	private void inlinedInitializationInConstructor() {
		this.pendingFeedbacksToReceive = new Map[(int) (FEEDBACK_EXPIRATION_TIME / MAINTENANCE_INTERVAL) + 1];
		this.pendingFeedbacksToSend = new Map[(int) (FEEDBACK_EXPIRATION_TIME / MAINTENANCE_INTERVAL) + 1];
	}

	public Node(InetAddress addr, Implementation implementation, EventingSystem eventingSystem, Properties properties) {
		this.properties = properties;
		if (properties == null || properties.isEmpty()) {
			throw new NullPointerException("SNDNode requires properties!");
		}
		this.address = addr;
		this.eventingSystem = eventingSystem;
		this.reputationModule = new ReputationModule(eventingSystem, this);
		this.implementation = implementation;

		this.aprioriNeighbors = new HashSet<>();
		this.introducedNeighbors = new LinkedHashMap<>();

		this.verbose = getBooleanProperty("snd.node.verbose", "false");
		// See #inlineThisInConstructor()
		inlinedInitializationInConstructor();

		for (int i = 0; i < pendingFeedbacksToReceive.length; i++) {
			pendingFeedbacksToReceive[i] = Collections.synchronizedMap(new HashMap<>());
			pendingFeedbacksToSend[i] = Collections.synchronizedMap(new HashMap<>());
		}

		if (properties.containsKey("snd.sim.verbose_on_IR")) {
			verboseOnIntroductionRequest = getLongProperty("snd.sim.verbose_on_IR");
		}

		if (properties.containsKey("snd.sim." + addrToString(address) + ".verbose")) {
			this.verbose = this.verbose || getBooleanProperty("snd.sim." + addrToString(address) + ".verbose", "false");
		}
		logger = Logger.getLogger("node" + addrToString(this.address));
		if (logger.getLevel() == null) {
			logger = LOGGER;
		} else if (LOGGER.getLevel() != null) {
			if (LOGGER.getLevel().intValue() < logger.getLevel().intValue()) {
				logger = LOGGER;
			}
		}
		eventingSystem.scheduleEventRelative(this, NODE_MAINTENANCE_EVENT,
				MAINTENANCE_INTERVAL + Math.abs(address.hashCode() % 100));
	}

	public final InetAddress getAddress() {
		return address;
	}

	/**
	 * Parse a property that specifies an integer.
	 * 
	 * @param propName the name of the property
	 * @return the value of the property
	 */
	public float getFloatProperty(String propName) {
		return PropertyParser.getFloatProperty(propName, properties);
	}

	public boolean getBooleanProperty(String propName, String defaultValue) {
		return PropertyParser.getBooleanProperty(propName, defaultValue, properties);
	}

	public int getIntegerProperty(String propName) {
		return PropertyParser.getIntegerProperty(propName, properties);
	}

	public long getLongProperty(String propName) {
		return PropertyParser.getLongProperty(propName, properties);
	}

	public double getProbabilityProperty(String propName) {
		return PropertyParser.getProbabilityProperty(propName, properties);
	}

	public synchronized Pedigree getPedigree(InetAddress client) {
		Pedigree p = pedigrees.get(client);
		if (p == null) {
//			if (router.isIntroducedNeighbor(client)) {
//				throw new IllegalArgumentException(this + " does not have a pedigree for neighbor " + client);
//			}
			p = new Pedigree(client);
			pedigrees.put(client, p);
		}
		return p;

	}

	public synchronized void addPedigree(Pedigree p) {
		// TODO Add code to manage the number of pedigrees stored, to check pedigree
		// length, to compare this pedigree to a previous one for the same subject, etc.
		pedigrees.put(p.getSubject(), p);
	}

	/**
	 * The method invoked when an SNDMessage, a WrappedMessage, or an unroutable
	 * application-layer Message is received. It is synchronized -- we process one
	 * message at a time!
	 * 
	 * @param m
	 */
	public synchronized void receive(Message m) {
		logger.fine(this + ": in node, received " + m);
		// Process the ACK even if it arrives on a VPN the node believes to be closed.
		// This makes the system more efficient in the face of out-of-order packet
		// deliveries.
		if (m instanceof AckMessage) {
			processAck((AckMessage) m);
			return;
		}
		InetAddress from = m.getSrc();
		if (!aprioriNeighbors.contains(from) && !introducedNeighbors.containsKey(from)) {
			// This node is in the process of closing the VPN. Probably. So, log that we are
			// dropping this message, and then drop it.
			logger.warning(addTimePrefix(this + ": received <" + m + "> (from <" + addrToString(from)
					+ "> on a VPN that does not exist. Ignoring it.\napriori neighbors: " + aprioriNeighbors));
			if (from.equals(address)) {
				new Exception("We sent " + m + " to ourselves!").printStackTrace();
				System.exit(-1);
			}
			return;
		}
		if (!(m instanceof SNDPMessage)) {
			processMessage(m);
			return;
		}
		AckMessage ack = new AckMessage((IntroductionMessage) m);
		try {
			implementation.getComms().send(ack);
		} catch (IOException e) {
			// We don't really care about failed ack transmissions
		}
		IntroductionMessage im = (IntroductionMessage) m;
		if (verboseOnIntroductionRequest != null && verboseOnIntroductionRequest == im.getIntroductionRequest().nonce) {
			verbose = true;
		}
		switch (im.getType()) {
		case ACK:
			break;
		case INTRODUCTION_DENIED:
		case INTRODUCTION_COMPLETED:
		case INTRODUCTION_ACCEPTED:
		case INTRODUCTION_REFUSED:
			StateMachine protocol = registeredProtocols.get(im.getIntroductionRequest());
			if (protocol == null) {
				logger.severe(addTimePrefix(this + ": received " + im + " but there is no protocol for it. Reason:\n"
						+ "\t" + recentlyUnregisteredProtocols.get(im.getIntroductionRequest())));
				return;
			}
			protocol.receive(im);
			return;
		case INTRODUCTION_REQUEST:
			processIntroductionRequest((IntroductionRequestMessage) m);
			break;
		case INTRODUCTION_OFFER:
			processIntroductionOffer((IntroductionOfferMessage) m);
			break;
		case ADD_INTRODUCTION_REQUEST:
			processAddIntroductionRequest((AddIntroductionRequestMessage) m);
			break;
		case FEEDBACK:
			processFeedback((FeedbackMessage) m);
			break;
		default:
			System.err.println("Do not know how to process message " + m);
			System.exit(-1);
		}
	}

	/**
	 * AddIntroductionRequestMessages are processed in the Node (as opposed to in a
	 * protocol) because it has to happen inside the node's receive(Message)
	 * semaphore.
	 * 
	 * @param m
	 */
	private void processAddIntroductionRequest(AddIntroductionRequestMessage m) {
		RequesterProtocol irp = (RequesterProtocol) registeredProtocols.get(m.getIntroductionRequest());
		if (irp == null) {
			logger.warning(addTimePrefix(this + ": Received " + m
					+ ", but there is no InitiateRequestProtocol registered. This could be a natural race-condition outcome.\n"
					+ "\tReason: " + recentlyUnregisteredProtocols.get(m.getIntroductionRequest())));
			return;
		}
		if (!addIntroductionRequestToVPN(m.getIntroductionRequest(), m.getSrc())) {
			logger.severe(this + ": invariant violation. Received " + m
					+ ", but addIntroductionRequestToVPN returned false. introducedNeighbors.keyset().contains(m.getSrc())="
					+ this.introducedNeighbors.keySet().contains(m.getSrc()));
			// For now, terminate
			new Exception().printStackTrace();
			System.exit(-1);
		}
		irp.receive(m);
	}

	private void processIntroductionOffer(IntroductionOfferMessage m) {
		if (registeredProtocols.containsKey(m.getIntroductionRequest())) {
			// this is a retransmission -- do not process this message!
			return;
		}
		TargetProtocol protocol = new TargetProtocol(this, m, verbose);
		registeredProtocols.put(m.getIntroductionRequest(), protocol);
		protocol.begin();
	}

	protected void processIntroductionRequest(IntroductionRequestMessage m) {
		if (registeredProtocols.containsKey(m.getIntroductionRequest())) {
			// this is a retransmission -- do not process this message!
			return;
		}
		IntroductionRequest irIn = m.getIntroductionRequest();
		if (!m.getSrc().equals(irIn.requester) || !m.getDst().equals(irIn.introducer)) {
			logger.severe(addTimePrefix(this + ": INVARIANT VIOLATION. m.getSrc()=" + addrToString(m.getSrc())
					+ ", ir.requester=" + addrToString(irIn.requester) + ", m.getDst()=" + addrToString(m.getDst())
					+ ", ir.introducer=" + addrToString(irIn.introducer)));
			return;
		}
		IntroductionRequest requesterIntroduction = null;
//		XXXXXXXXXXXXXX
//		Race Condition! The introduction request got here before the ACK. 
		// (Before what ACK? I wish I had written more text here; I do not remember the mechanics of this bug...)
//		Solution: include the requesterIntroduction (the preceding introduction request)
//		in the next introduction request, rather than counting on the introducer being
//		able to find the introduction in its tables.
//		XXXXXXXXXXXXXX
		if (introducedNeighbors.containsKey(irIn.requester)) {
			Set<IntroductionRequest> introductions = introducedNeighbors.get(irIn.requester);
			for (IntroductionRequest ir : introductions) {
				if (ir.requester.equals(irIn.requester) && ir.destination.equals(irIn.destination)) {
					requesterIntroduction = ir;
					break;
				}
			}
			if (requesterIntroduction == null) {
				requesterIntroduction = m.getPreviousIntroductionRequest();
//				StringBuffer msg = new StringBuffer(this + " INVARIANT VIOLATION in processIntroductionRequest()\n");
//				msg.append("\tInvoked in response to event " + m);
//				msg.append("\tintroductions=" + introductions);
//				logger.severe(addTimePrefix(msg.toString()));
//				new Exception().printStackTrace();
//				System.exit(-1);
			}
		}
		if ((requesterIntroduction == null && (m.getPreviousIntroductionRequest() != null)) || //
				(requesterIntroduction != null && m.getPreviousIntroductionRequest() == null)) {
			StringBuffer msg = new StringBuffer(this + " INVARIANT VIOLATION in processIntroductionRequest()\n");
			msg.append("\tInvoked in response to event " + m + "\n");
			msg.append("requesterIntroduction=" + requesterIntroduction + "\n");
			msg.append("m.getPreviousIntroductionRequest()=" + m.getPreviousIntroductionRequest() + "\n");
			logger.severe(addTimePrefix(msg.toString()));
			new Exception().printStackTrace();
			System.exit(-1);
		}
		IntroducerProtocol protocol = new IntroducerProtocol(this, m, requesterIntroduction, verbose);
		registeredProtocols.put(m.getIntroductionRequest(), protocol);
		protocol.begin();
	}

	private void processAck(AckMessage m) {
		long id = m.getIdentifier();
		SNDPMessageTransmissionProtocol protocol = ackWaiters.remove(id);
		if (protocol != null) {
			protocol.receive(m);
		}
	}

	/**
	 * Processing non-SNDMessage messages.
	 * 
	 * @param m the received message
	 * 
	 * @see #receive(Message)
	 * @see ProxyNode#receive(Message)
	 * @see ServerProxy#receive(Message)
	 */
	protected void processMessage(Message m) {
		// For now, you need to have subclassed SNDNode to support a node receiving (or
		// sending!) application messages.
		new Exception(this + ": received a message: " + m + "  class=" + m.getClass()).printStackTrace();
		System.exit(-1);
	}

	public boolean evaluatePedigree(Pedigree p) {
		addPedigree(p);
		return reputationModule.reputationIsGreaterThanThreshold(p, verbose);
	}

	private IntroductionRequest removePendingFeedbacksToReceive(IntroductionRequest ir)
			throws IllegalArgumentException {
		for (int i = 0; i < pendingFeedbacksToReceive.length; i++) {
			if (pendingFeedbacksToReceive[i].containsKey(ir)) {
				return pendingFeedbacksToReceive[i].remove(ir);
			}
		}
		throw new IllegalArgumentException();
	}

	private boolean pendingFeedbacksToSendContainsKey(AddressPair srcDst) {
		for (int i = 0; i < pendingFeedbacksToSend.length; i++) {
			if (pendingFeedbacksToSend[i].containsKey(srcDst)) {
				pendingFeedbacksToSend[i].remove(srcDst);
				return true;
			}
		}
		return false;
	}

	protected void processFeedback(FeedbackMessage m) {
		if (logger.isLoggable(FINE)) {
			logger.fine(this + " received " + m);
		}
		IntroductionRequest introductionRequest = m.getIntroductionRequest();
		if (m.getFeedback() == Feedback.BAD) {
			removeAllIntroductionRequestsFromVPN(introductionRequest.requester);
		}
		logger.finest(this + ": the feedback regards " + introductionRequest);
		if (!pendingFeedbacksToReceive[0].containsKey(introductionRequest)) {
		}
		Pedigree pedigree = getPedigree(introductionRequest.requester);
		if (pedigree == null) {
			logger.warning(this + ": Received feedback for a client we no longer know. m=" + m);
			return;
		}
		logger.finest(this + ": the pedigree of the requester: " + pedigree);
		IntroductionRequest previousIntroduction = null;
		try {
			previousIntroduction = removePendingFeedbacksToReceive(introductionRequest);
		} catch (IllegalArgumentException e) {
			logger.severe(this + ": Received feedback for a transaction that is not pending feedback. m=" + m);
			new Exception(this + ": Received feedback for a transaction that is not pending feedback. m=" + m)
					.printStackTrace();
			return;
		}
		reputationModule.applyFeedback(pedigree, m.getFeedback());
		if (previousIntroduction != null) {
			AddressPair ap = new AddressPair(previousIntroduction.requester, previousIntroduction.destination);
			if (!pendingFeedbacksToSendContainsKey(ap)) {
				logger.warning(this + ": no pending feedback! feedback=" + m + ", previousIntroduction="
						+ previousIntroduction + ", outgoingIntroduction=" + introductionRequest); // The feedback is
																									// too old. Ignore
																									// it.
				return;
			}
			if (pedigree == null || pedigree.getRequestSequence().length == 0) {
				new Exception(this + ": Invariant Violation: we think we should forward " + m
						+ ", but there is no previous pedigree. Pedigree=" + pedigree).printStackTrace();
				System.exit(-1);
			}
			FeedbackMessage fm = new FeedbackMessage(previousIntroduction, getAddress(), m.getSubject(),
					m.getFeedback(), m.getTrigger());
			// System.out.println(this + " sending (fwding) " + fm);
			send(fm);
		} else {
			if (pedigree.getRequestSequence().length != 0) {
				new Exception(
						"Invariant Violation: we do not have an introducer for this transaction in the pendingFeedbacksToSend, but there are one or more introducers in the pedigree.")
						.printStackTrace();
				System.exit(-1);
			}
			if (aprioriNeighbors.contains(introductionRequest.requester)) { // We are connected to the requester --
																			// forward the feedback to them!
				send(new FeedbackMessage(introductionRequest.requester, getAddress(), m.getSubject(), m.getFeedback(),
						m.getTrigger()));
			} else {
				new Exception(this
						+ ": Invariant Violation: there are no introducers, but the requester is not a long-lived neighbor. ir.requester="
						+ introductionRequest.requester).printStackTrace();
				System.exit(-1);
			}
		}
	}

	public void send(Message m) {
		if (!m.getClass().equals(Message.class)) {
			new Exception("Being asked to send something that isn't a message!").printStackTrace();
			System.exit(-1);
		}
		try {
			implementation.getComms().send(m);
		} catch (IOException e) {
			// Do not do anything if a user message transmission fails
			e.printStackTrace();
		}
	}

	protected void send(FeedbackMessage fm) {
		SNDPMessageTransmissionProtocol sendingProto = new SNDPMessageTransmissionProtocol(this, null, fm, verbose);
		sendingProto.begin();
	}

	@Override
	public String toString() {
		return "Node-" + addrToString(getAddress());
	}

	public EventingSystem getEventingSystem() {
		return eventingSystem;
	}

	@Override
	public void process(Event e, EventingSystem eventingSystem, long currentTime) {
		if (e instanceof VPNClosedEvent) {
			InetAddress nbr = ((VPNClosedEvent) e).nbr;
			privateVPNClosed(nbr);
			return;
		}
		if (e == NODE_MAINTENANCE_EVENT) {
			int i = currentPendingFeedbacks;
			i++;
			if (i == pendingFeedbacksToSend.length) {
				i = 0;
			}
			currentPendingFeedbacks = i;
			pendingFeedbacksToSend[i].clear();
			pendingFeedbacksToReceive[i].clear();
			eventingSystem.scheduleEventRelative(this, e, MAINTENANCE_INTERVAL);
		}
		if (e instanceof Message) {
			this.receive((Message) e);
		}
	}

	public Logger getLogger() {
		return logger;
	}

	public synchronized void send(SNDPMessageTransmissionProtocol sender, SNDPMessage message) {
		try {
			implementation.getComms().send(message);
			ackWaiters.put(message.getIdentifier(), sender);
		} catch (IOException e) {
			sender.receive(StateMachine.FAILURE_EVENT);
		}
	}

	public synchronized void send(IntroductionProtocol protocol, IntroductionMessage message) {
		send(protocol, message, null);
	}

	/**
	 * Send an {@link IntroductionMessage} on behalf of the specified protocol. If
	 * <code>callback</code> is null, the protocol will be sent
	 * {@link StateMachine#SUCCESS_EVENT} when an {@link AckMessage} is received or
	 * {@link StateMachine#FAILURE_EVENT} if there are no {@link AckMessage}s after
	 * N attempts. If <code>callback</code> is not null, then the callback will be
	 * invoked when the {@link SNDPMessageTransmissionProtocol} enters a terminal
	 * state.
	 * 
	 * @param protocol the introduction protocol that is responsible for sending
	 *                 this message
	 * @param message  the message being sent
	 * @param callback if non-null, this callback is invoked when the
	 *                 {@link SNDPMessageTransmissionProtocol} enters a terminal
	 *                 state
	 */
	public synchronized void send(IntroductionProtocol protocol, IntroductionMessage message,
			StateMachine.StateMachineTracker callback) {
		if (!registeredProtocols.containsKey(protocol.getIntroductionRequest())) {
			registeredProtocols.put(protocol.getIntroductionRequest(), protocol);
		}
		SNDPMessageTransmissionProtocol stp = null;
		if (callback == null) {
			stp = new SNDPMessageTransmissionProtocol(this, protocol, message, protocol.getVerbose());
		} else {
			stp = new SNDPMessageTransmissionProtocol(this, null, message, protocol.getVerbose());
			stp.registerCallback(callback);
		}
		stp.begin();
	}

	/**
	 * 
	 * @param introductionRequest
	 * @param nbr
	 * @return true if the neighbor is in our set of introduced neighbors.
	 */
	public synchronized boolean addIntroductionRequestToVPN(IntroductionRequest introductionRequest, InetAddress nbr) {
		Set<IntroductionRequest> requests = introducedNeighbors.get(nbr);
		if (requests == null) {
			return false;
		}
		requests.add(introductionRequest);
		return true;
	}

	public synchronized void removeAllIntroductionRequestsFromVPN(InetAddress nbr) {
		Set<IntroductionRequest> r = introducedNeighbors.get(nbr);
		if (r == null) {
			return;
		}
		Set<IntroductionRequest> reqs = new HashSet<>();
		reqs.addAll(r);
		for (IntroductionRequest ir : reqs) {
			removeIntroductionRequestFromVPN(ir, nbr);
		}
	}

	public synchronized void removeIntroductionRequestFromVPN(IntroductionRequest introductionRequest,
			InetAddress nbr) {
		Set<IntroductionRequest> requests = introducedNeighbors.get(nbr);
		if (requests == null) {
			// This probably should never happen.
			logger.warning(this + ": attempted to remove <" + introductionRequest + "> from a non-existant VPN!");
			return;
		}
		if (verbose) {
			System.out.println(this + ": Removing IR " + introductionRequest + " from VPN to " + addrToString(nbr));
		}
		requests.remove(introductionRequest);
		if (requests.isEmpty()) {
			if (verbose) {
				System.out.println(this + ": closing VPN to " + addrToString(nbr));
			}
			introducedNeighbors.remove(nbr);
			implementation.getVPNManager().closeVPN(nbr);
			implementation.getComms().removeRoutesVia(nbr);
		} else {
			if (verbose) {
				System.out.println(
						this + ": there are " + requests.size() + " IRs remaining on the VPN to " + addrToString(nbr));
			}
		}
	}

	public synchronized void closeVPN(InetAddress nbr) throws IOException {
		if (!aprioriNeighbors.contains(nbr)) {
			throw new IOException(nbr + " is not an a-priori neighbor.");
		}
		aprioriNeighbors.remove(nbr);
		implementation.getVPNManager().closeVPN(nbr);
		implementation.getComms().removeRoutesVia(nbr);
	}

	public Serializable generateKeyingMaterial() {
		return "keying material";
	}

	public synchronized void createVPN(InetAddress nbr, Object keyingMaterial) throws IOException {
		if (aprioriNeighbors.contains(nbr)) {
			return;
		}
		implementation.getVPNManager().createVPN(nbr, keyingMaterial);
		aprioriNeighbors.add(nbr);
		return;
	}

	public synchronized boolean createVPN(InetAddress nbr, IntroductionRequest introductionRequest,
			Object keyingMaterial) {
		if (introductionRequest == null) {
			throw new NullPointerException("There must be a non-null introduction request.");
		}
		Set<IntroductionRequest> requests = introducedNeighbors.get(nbr);
		if (requests != null) {
			if (verbose) {
				System.out.println(this + ": VPN to " + addrToString(nbr) + " exists, adding " + introductionRequest);
			}
			requests.add(introductionRequest);
		} else {
			try {
				implementation.getVPNManager().createVPN(nbr, keyingMaterial);
			} catch (IOException e) {
				return false;
			}
			requests = new HashSet<>();
			requests.add(introductionRequest);
			introducedNeighbors.put(nbr, requests);
			if (verbose) {
				System.out.println(this + ": created VPN to " + addrToString(nbr) + " with IR " + introductionRequest);
			}
		}
		while (introducedNeighbors.size() > MAX_INTRODUCED_NEIGHBORS) {
			logger.fine(this
					.addTimePrefix(this + ": deleting oldest VPNs due to exceeding MAX_INTRODUCED_NEIGHBORS!!"));
			Iterator<InetAddress> iter = introducedNeighbors.keySet().iterator();
			InetAddress n = iter.next();
			removeAllIntroductionRequestsFromVPN(n);
		}
		return true;
	}

	public synchronized void unregisterProtocol(IntroductionProtocol proto, String reason) {
		IntroductionProtocol p = registeredProtocols.remove(proto.getIntroductionRequest());
		if (p != proto) {
			System.out.println("We are not dealing with individual protocol instances!?");
		}
		recentlyUnregisteredProtocols.put(p.getIntroductionRequest(), reason);
	}

	public InetAddress getNextHopTo(InetAddress destination) {
		Query query = implementation.getDiscoveryService().createQuery(destination);
		return implementation.getDiscoveryService().getNextHopTo(query);
	}

	public void addRoute(InetAddress dst, InetAddress target) {
		implementation.getComms().addRoute(dst, target);
	}

	public synchronized void unregisterAckWaiter(long id) {
		ackWaiters.remove(id);
	}

	@Override
	public void vpnClosed(InetAddress nbr) {
		eventingSystem.scheduleEvent(this, new VPNClosedEvent(nbr));
	}

	private synchronized void privateVPNClosed(InetAddress nbr) {
		implementation.getComms().removeRoutesVia(nbr);
		if (aprioriNeighbors.contains(nbr)) {
			aprioriNeighbors.remove(nbr);
			return;
		}
		if (introducedNeighbors.containsKey(nbr)) {
			introducedNeighbors.remove(nbr);
		}
	}

	public synchronized void addPendingFeedbackToReceive(IntroductionRequest introductionRequest,
			IntroductionRequest requesterIntroduction) {
		pendingFeedbacksToReceive[currentPendingFeedbacks].put(introductionRequest, requesterIntroduction);
	}

	public synchronized void addPendingFeedbackToSend(IntroductionRequest introductionRequest) {
		pendingFeedbacksToSend[currentPendingFeedbacks].put(
				new AddressPair(introductionRequest.requester, introductionRequest.destination), introductionRequest);
	}

	/**
	 * Given the identity of a (perhaps former) neighbor, get the introduction
	 * request that led to its being a neighbor.
	 * 
	 * @param networkSrc
	 * @return
	 */
	protected IntroductionRequest getPendingFeedbackTo(InetAddress networkSrc, InetAddress networkDst) {
		AddressPair srcDst = new AddressPair(networkSrc, networkDst);
		for (int i = 0; i < pendingFeedbacksToSend.length; i++) {
			int index = currentPendingFeedbacks - i;
			if (index < 0) {
				index += pendingFeedbacksToSend.length;
			}
			if (pendingFeedbacksToSend[index].containsKey(srcDst)) {
				return pendingFeedbacksToSend[index].get(srcDst);
			}
		}
		return null;
	}

	public String addTimePrefix(String msg) {
		return String.format("%10.3f: %s", ((float) eventingSystem.getCurrentTime()) / 1000.0, msg);
	}

	public boolean checkIntroductionRequestNonce(long nonce) {
		if (verboseOnIntroductionRequest != null && verboseOnIntroductionRequest == nonce) {
			verbose = true;
		}
		return verbose;
	}

	private static class VPNClosedEvent implements Event {
		public final InetAddress nbr;

		public VPNClosedEvent(InetAddress nbr) {
			this.nbr = nbr;
		}
	}

	public long getCurrentTime() {
		return eventingSystem.getCurrentTime();
	}

	public long getPendingFeedbacksSize() {
		long total = 0;
		for (int i = 0; i < pendingFeedbacksToSend.length; i++) {
			total += pendingFeedbacksToSend[i].size() + pendingFeedbacksToReceive[i].size();
		}
		return total;
	}

}
