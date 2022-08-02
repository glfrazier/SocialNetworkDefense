package com.github.glfrazier.snd.node;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;
import static java.util.logging.Level.FINE;

import java.io.IOException;
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
import com.github.glfrazier.snd.protocol.RequesterProtocol;
import com.github.glfrazier.snd.protocol.IntroductionProtocol;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.Pedigree;
import com.github.glfrazier.snd.protocol.TargetProtocol;
import com.github.glfrazier.snd.protocol.IntroducerProtocol;
import com.github.glfrazier.snd.protocol.SNDPMessageTransmissionProtocol;
import com.github.glfrazier.snd.protocol.message.AckMessage;
import com.github.glfrazier.snd.protocol.message.AddIntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.FeedbackMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionOfferMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.protocol.message.SNDPMessage;
import com.github.glfrazier.snd.util.DiscoveryService.Query;
import com.github.glfrazier.snd.util.Implementation;
import com.github.glfrazier.snd.util.PropertyParser;
import com.github.glfrazier.snd.util.TimeAndIntroductionRequest;
import com.github.glfrazier.statemachine.StateMachine;

/**
 * A node in the Social Network Defense network. Implements the SND protocol for
 * establishing VPNs between trusted entities.
 */
public class Node implements EventProcessor, MessageReceiver {

	private final InetAddress address;

	/**
	 * How the nodes that have been introduced to this node got here.
	 */
	private final Map<InetAddress, Pedigree> pedigrees = new HashMap<>();

	/**
	 * The keys of this map are the set of accepted offers that this node is waiting
	 * to send feedback about. The value for each key is the timestamp (in
	 * milliseconds) when the introduction request was accepted. After a suitable
	 * timeout (24 hrs?), the entry is discarded.
	 */
	private Map<IntroductionRequest, Long> pendingFeedbacksToSend = Collections.synchronizedMap(new LinkedHashMap<>());

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
	private Map<IntroductionRequest, TimeAndIntroductionRequest> pendingFeedbacksToReceive = Collections
			.synchronizedMap(new HashMap<>());

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

		public String toString() {
			return name;
		}
	};

	/**
	 * The maximum time lapse between an introduction and feedback regarding that
	 * introduction.
	 */
	// TODO These constants should all be set by properties!
	protected static final long FEEDBACK_EXPIRATION_TIME = 8 * 60 * 60 * 1000; // eight hours (why?)
	private static final long MAINTENANCE_INTERVAL = FEEDBACK_EXPIRATION_TIME / 100;
	private static final int MAX_INTRODUCED_NEIGHBORS = 100; 
	
	protected static final Logger LOGGER = Logger.getLogger(Node.class.getName());

	protected Logger logger;

	private Map<Long, SNDPMessageTransmissionProtocol> ackWaiters = Collections.synchronizedMap(new HashMap<>());

	private Map<IntroductionRequest, StateMachine> registeredProtocols = Collections.synchronizedMap(new HashMap<>());

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
		if (!(m instanceof SNDPMessage)) {
			processMessage(m);
			return;
		}
		if (m instanceof AckMessage) {
			processAck((AckMessage) m);
			return;
		}
		AckMessage ack = new AckMessage((IntroductionMessage) m);
		try {
			implementation.getComms().send(ack);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		IntroductionMessage im = (IntroductionMessage) m;
		switch (im.getType()) {
		case ACK:
			break;
		case INTRODUCTION_DENIED:
		case INTRODUCTION_COMPLETED:
		case INTRODUCTION_ACCEPTED:
		case INTRODUCTION_REFUSED:
			StateMachine protocol = registeredProtocols.get(im.getIntroductionRequest());
			if (protocol == null) {
				LOGGER.severe(this + ": received " + im + " but there is no protocol for it.");
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
			LOGGER.severe(this + ": invariant violation. Received " + m
					+ ", but there is no InitiateRequestProtocol registered.");
			// For now, terminate
			System.exit(-1);
		}
		if (!addIntroductionRequestToVPN(m.getIntroductionRequest(), m.getSrc())) {
			LOGGER.severe(this + ": invariant violation. Received " + m
					+ ", but addIntroductionRequestToVPN returned false.");
			// For now, terminate
			new Exception().printStackTrace();
			System.exit(-1);
		}
		irp.receive(m);
	}

	private void processIntroductionOffer(IntroductionOfferMessage m) {
		TargetProtocol protocol = new TargetProtocol(this, m, verbose);
		registeredProtocols.put(m.getIntroductionRequest(), protocol);
		protocol.begin();
	}

	protected void processIntroductionRequest(IntroductionRequestMessage m) {
		IntroducerProtocol protocol = new IntroducerProtocol(this, m, verbose);
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

	protected void processFeedback(FeedbackMessage m) {
		if (logger.isLoggable(FINE)) {
			logger.fine(this + " received " + m);
		}
		IntroductionRequest introductionRequest = m.getIntroductionRequest();
		logger.finest(this + ": the feedback regards " + introductionRequest);
		if (!pendingFeedbacksToReceive.containsKey(introductionRequest)) {
			logger.severe(this + ": Received feedback for a transaction that is not pending feedback. m=" + m);
			return;
		}
		Pedigree pedigree = getPedigree(introductionRequest.requester);
		if (pedigree == null) {
			// TODO should we manufacture an introducer-less pedigree?
			logger.warning(this + ": Received feedback for a client we no longer know. m=" + m);
			return;
		}
		logger.finest(this + ": the pedigree of the requester: " + pedigree);
		reputationModule.applyFeedback(pedigree, m.getFeedback());
		TimeAndIntroductionRequest tir = pendingFeedbacksToReceive.remove(introductionRequest);
		if (tir == null) {
			logger.fine(this + ": tir == null!");
		} else if (tir.ir == null) {
			logger.fine(this + ": tir.ir == null!");
		}
		IntroductionRequest previousIntroduction = (tir == null ? null : tir.ir);
		if (previousIntroduction != null) {
			if (!pendingFeedbacksToSend.containsKey(previousIntroduction)) {
				logger.warning(this + ": no pending feedback! feedback=" + m + ", previousIntroduction="
						+ previousIntroduction + ", outgoingIntroduction=" + introductionRequest);
				// The feedback is too old. Ignore it.
				return;
			}
			pendingFeedbacksToSend.remove(previousIntroduction);
			if (pedigree == null || pedigree.getRequestSequence().length == 0) {
				new Exception(this + ": Invariant Violation: we think we should forward " + m
						+ ", but there is no previous pedigree. Pedigree=" + pedigree).printStackTrace();
				System.exit(-1);
			}
			FeedbackMessage fm = new FeedbackMessage(previousIntroduction, getAddress(), m.getSubject(),
					m.getFeedback());
			// System.out.println(this + " sending (fwding) " + fm);
			send(fm);
		} else {
			if (pedigree.getRequestSequence().length != 0) {
				new Exception(
						"Invariant Violation: we do not have an introducer for this transaction in the pendingFeedbacksToSend, but there are one or more introducers in the pedigree.")
						.printStackTrace();
				System.exit(-1);
			}
			if (aprioriNeighbors.contains(introductionRequest.requester)) {
				// We are connected to the requester -- forward the feedback to them!
				send(new FeedbackMessage(introductionRequest.requester, getAddress(), m.getSubject(), m.getFeedback()));
			} else {
				new Exception(this
						+ ": Invariant Violation: there are no introducers, but the requester is not a long-lived neighbor. ir.requester="
						+ introductionRequest.requester).printStackTrace();
				System.exit(-1);
			}
		}
	}

	public void send(Message m) {
		try {
			implementation.getComms().send(m);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void send(FeedbackMessage fm) {
		send(new SNDPMessageTransmissionProtocol(this, null, fm, verbose), fm);
	}

	@Override
	public String toString() {
		return "Node-" + addrToString(getAddress());
	}

	public EventingSystem getEventingSystem() {
		return eventingSystem;
	}

	@Override
	public void process(Event e, EventingSystem eventingSystem) {
		if (e == NODE_MAINTENANCE_EVENT) {
			Thread t = new Thread() {
				public void run() {
					long now = eventingSystem.getCurrentTime();
					synchronized (pendingFeedbacksToSend) {
						Iterator<IntroductionRequest> iter = pendingFeedbacksToSend.keySet().iterator();
						while (iter.hasNext()) {
							IntroductionRequest ir = iter.next();
							long time = pendingFeedbacksToSend.get(ir);
							if (now - time < FEEDBACK_EXPIRATION_TIME) {
								break;
							}
							iter.remove();
						}
					}
					synchronized (pendingFeedbacksToReceive) {
						Iterator<IntroductionRequest> iter = pendingFeedbacksToReceive.keySet().iterator();
						while (iter.hasNext()) {
							IntroductionRequest ir = iter.next();
							long time = pendingFeedbacksToReceive.get(ir).time;
							if (now - time < FEEDBACK_EXPIRATION_TIME) {
								break;
							}
							iter.remove();
						}
					}
				}
			};
			t.start();
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
		} catch (IOException e) {
			sender.receive(StateMachine.FAILURE_EVENT);
		}
		ackWaiters.put(message.getIdentifier(), sender);
	}

	public synchronized void send(IntroductionProtocol protocol, IntroductionMessage message) {
		if (!registeredProtocols.containsKey(protocol.getIntroductionRequest())) {
			registeredProtocols.put(protocol.getIntroductionRequest(), protocol);
		}
		SNDPMessageTransmissionProtocol stp = new SNDPMessageTransmissionProtocol(this, protocol, message, verbose);
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

	public synchronized void removeIntroductionRequestFromVPN(IntroductionRequest introductionRequest,
			InetAddress nbr) {
		Set<IntroductionRequest> requests = introducedNeighbors.get(nbr);
		if (requests == null) {
			LOGGER.severe(this + ": Removing an introduction request from a non-existant VPN!");
			System.exit(-1);
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

	public Object generateKeyingMaterial() {
		// TODO Auto-generated method stub
		return new Object() {
			public String toString() {
				return "keying material";
			}
		};
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
				System.out.println(this + ": VPN to " + addrToString(nbr)+ " exists, adding " + introductionRequest);
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
				System.out.println(this + ": created VPN to " + addrToString(nbr) + " with IR " + introductionRequest );
			}
		}
		while (introducedNeighbors.size() > MAX_INTRODUCED_NEIGHBORS) {
			Iterator<InetAddress> iter = introducedNeighbors.keySet().iterator();
			InetAddress n = iter.next();
			Set<IntroductionRequest> reqs = new HashSet<>();
			reqs.addAll(introducedNeighbors.get(n));
			for(IntroductionRequest ir : reqs) {
				removeIntroductionRequestFromVPN(ir, n);
			}
		}
		return true;
	}

	public synchronized void unregisterProtocol(IntroductionProtocol proto) {
		registeredProtocols.remove(proto.getIntroductionRequest());
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
	public synchronized void vpnClosed(InetAddress nbr) {
		implementation.getComms().removeRoutesVia(nbr);
		if (aprioriNeighbors.contains(nbr)) {
			aprioriNeighbors.remove(nbr);
			return;
		}
		if (introducedNeighbors.containsKey(nbr)) {
			introducedNeighbors.remove(nbr);
		}
	}

}
