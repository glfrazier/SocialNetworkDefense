package com.github.glfrazier.snd.node;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventProcessor;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.Pedigree;
import com.github.glfrazier.snd.protocol.message.AckMessage;
import com.github.glfrazier.snd.protocol.message.FeedbackMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionAcceptedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionCompletedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedWillRouteMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionOfferMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRefusedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.protocol.message.SNDPMessage;
import com.github.glfrazier.snd.protocol.message.WrappedMessage;
import com.github.glfrazier.snd.util.DiscoveryService;
import com.github.glfrazier.snd.util.Implementation;
import com.github.glfrazier.snd.util.PropertyParser;
import com.github.glfrazier.snd.util.TimeAndIntroductionRequest;

/**
 * A node in the Social Network Defense network. Implements the SND protocol for
 * establishing VPNs between trusted entities.
 */
public class SNDNode implements EventProcessor {

	private final InetAddress address;

	private final Map<InetAddress, Pedigree> pedigrees = new HashMap<>();

	/**
	 * The key set is the set of accepted offers that this node is waiting to send
	 * feedback about. The value is the timestamp (in milliseconds) when the
	 * introduction request was accepted. After a suitable timeout (24 hrs?), the
	 * entry is discarded.
	 */
	private Map<IntroductionRequest, Long> pendingFeedbacksToSend = Collections.synchronizedMap(new LinkedHashMap<>());

	/**
	 * The key set of this Map is the introduction requests that we have offered and
	 * had accepted, but not yet received feedback about. The values are the
	 * introduction requests by which we know the requester. If the requester is
	 * known to this node via a long-lived VPN, then the value is null.
	 */
	private Map<IntroductionRequest, TimeAndIntroductionRequest> pendingFeedbacksToReceive = Collections
			.synchronizedMap(new HashMap<>());

	public final Router router;
	protected final ReputationModule reputationModule;
	protected final EventingSystem eventingSystem;
	protected final Implementation implementation;
	private final Properties properties;

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
	protected static final long FEEDBACK_EXPIRATION_TIME = 8 * 60 * 60 * 1000; // eight hours (why?)
	private static final long MAINTENANCE_INTERVAL = FEEDBACK_EXPIRATION_TIME / 100;
	protected static final Logger LOGGER = Logger.getLogger(SNDNode.class.getName());
	protected Logger logger;

	public SNDNode(InetAddress addr, Implementation implementation, EventingSystem eventingSystem,
			Properties properties) {
		this.properties = properties;
		if (properties == null || properties.isEmpty()) {
			throw new NullPointerException("SNDNode requires properties!");
		}
		this.address = addr;
		this.eventingSystem = eventingSystem;
		this.router = new Router(this);
		this.reputationModule = new ReputationModule(eventingSystem, this);
		this.implementation = implementation;
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
			if (router.isIntroducedNeighbor(client)) {
				throw new IllegalArgumentException(this + " does not have a pedigree for neighbor " + client);
			}
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
		IntroductionMessage msg = (IntroductionMessage) m;
		switch (msg.getType()) {
		case INTRODUCTION_DENIED:
			processIntroductionDenied((IntroductionDeniedMessage) m);
			break;
		case INTRODUCTION_DENIED_WILL_ROUTE:
			processIntroductionDeniedWillRoute((IntroductionDeniedWillRouteMessage) m);
			break;
		case INTRODUCTION_COMPLETED:
			processIntroductionCompleted((IntroductionCompletedMessage) m);
			break;
		case INTRODUCTION_REQUEST:
			processIntroductionRequest((IntroductionRequestMessage) m);
			break;
		case INTRODUCTION_OFFER:
			processIntroductionOffer((IntroductionOfferMessage) m);
			break;
		case INTRODUCTION_ACCEPTED:
		case INTRODUCTION_REFUSED:
			processTargetResponse((IntroductionMessage) m);
			break;
		case FEEDBACK:
			processFeedback((FeedbackMessage) m);
			break;
		default:
			System.err.println("Do not know how to process message " + m);
			System.exit(-1);
		}
	}

	protected void processIntroductionCompleted(IntroductionCompletedMessage m) {
		// ClientImpl overrides receive(). So, we should never encounter an Introduction
		// Completed message here.
		new Exception(this + ": This method should never be invoked!").printStackTrace();
		System.exit(-1);
	}

	protected void processIntroductionDenied(IntroductionDeniedMessage m) {
		// ClientImpl overrides receive(). So, we should never encounter an Introduction
		// Completed message here.
		new Exception(this + ": This method should never be invoked!").printStackTrace();
		System.exit(-1);
	}

	protected void processIntroductionDeniedWillRoute(IntroductionDeniedWillRouteMessage m) {
		// ClientImpl overrides receive(). So, we should never encounter an Introduction
		// Completed message here.
		new Exception(this + ": This method should never be invoked!").printStackTrace();
		System.exit(-1);
	}

	/**
	 * Processing non-SNDMessage messages.
	 * 
	 * @param m the received message
	 * 
	 * @see #receive(Message)
	 * @see ClientProxy#receive(Message)
	 * @see ServerProxy#receive(Message)
	 */
	protected void processMessage(Message m) {
		if (m instanceof WrappedMessage) {
			WrappedMessage wrapper = (WrappedMessage) m;
			Message enclosed = wrapper.getEnclosedMessage();
			try {
				// SNDMessages are never wrapped. We put this code here instead of in
				// ClientProxy or ServerProxy because this should *always* be the correct thing
				// to do with a wrapped message.
				router.send(enclosed);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}
		// For now, you need to have subclassed SNDNode to support a node receiving (or
		// sending!) application messages.
		new Exception(this + ": received an unwrapped message: " + m + "  class=" + m.getClass()).printStackTrace();
		System.exit(-1);
	}

	protected void processFeedback(FeedbackMessage m) {
		System.out.println(this + ": processing " + m);
		if (logger.isLoggable(FINE)) {
			logger.fine(this + " received " + m);
		}
		try {
			router.send(new AckMessage(m.getSrc(), getAddress()));
		} catch (IOException e1) {
			// Ignore a failed ack.
			e1.printStackTrace();
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
			try {
				FeedbackMessage fm = new FeedbackMessage(previousIntroduction, getAddress(), m.getSubject(),
						m.getFeedback());
				// System.out.println(this + " sending (fwding) " + fm);
				router.send(fm);
			} catch (IOException e) {
				logger.severe(this + ": Failed transmission: " + e);
				new Exception(this + ": Failed transmission: " + e).printStackTrace();
				System.exit(-1);
			}
		} else {
			if (pedigree.getRequestSequence().length != 0) {
				new Exception(
						"Invariant Violation: we do not have an introducer for this transaction in the pendingFeedbacksToSend, but there are one or more introducers in the pedigree.")
						.printStackTrace();
				System.exit(-1);
			}
			if (!router.isIntroducedNeighbor(introductionRequest.requester)) {
				// We are connected to the requester -- forward the feedback to them!
				try {
					router.send(new FeedbackMessage(introductionRequest.requester, getAddress(), m.getSubject(),
							m.getFeedback()));
				} catch (IOException e) {
					logger.severe(this + ": Failed transmission: " + e);
					new Exception(this + ": Failed transmission: " + e).printStackTrace();
					System.exit(-1);
				}
			} else {
				new Exception(this
						+ ": Invariant Violation: there are no introducers, but the requester is not a long-lived neighbor. ir.requester="
						+ introductionRequest.requester).printStackTrace();
				System.exit(-1);
			}
		}
	}

	/**
	 * 
	 * @param m
	 * @return true if the offer was accepted
	 */
	protected boolean processIntroductionOffer(IntroductionOfferMessage m) {
		Pedigree p = m.getPedigree();
		// System.out.println(this + " received " + m + " with pedigree " + p);
		p = p.getNext(m.getIntroductionRequest());
		// System.out.println("\tpedigree is now " + p);
		addPedigree(p);
		if (reputationModule.reputationIsGreaterThanThreshold(p, verbose)) {
			LOGGER.finer(this + ": accepting " + m.getIntroductionRequest());
			// If the client's reputation is above threshold, create a transaction-specific
			// VPN to the client and send an Introduction Accepted message.
			try {
				router.openIntroducedLink(m.getIntroductionRequest().requester, m.getIntroductionRequest(),
						m.getKeyingMaterial());
				router.send(new IntroductionAcceptedMessage(m.getIntroductionRequest(), getAddress()));
				// Add an entry to the pendingFeedbacksToSend, identifying the introducer as the
				// node to send the feedback to
				if (logger.isLoggable(FINEST)) {
					logger.finest(this + ": recording " + m.getIntroductionRequest() + " in pendingFeedbacksToSend.");
				}
				pendingFeedbacksToSend.put(m.getIntroductionRequest(), eventingSystem.getCurrentTime());
			} catch (IOException e) {
				logger.severe("Failed to send message: " + e);
			}
			return true;
		} else {
			LOGGER.finer(this + ": rejecting " + m.getIntroductionRequest());
			try {
				router.send(new IntroductionRefusedMessage(m.getIntroductionRequest(), getAddress()));
			} catch (IOException e) {
				logger.severe("Failed to send message: " + e);
			}
			return false;
		}

	}

	private void processTargetResponse(IntroductionMessage msg) {
		try {
			router.send(new AckMessage(msg.getSrc(), getAddress()));
		} catch (IOException e) {
			logger.severe(this + ": Failed to acknowledge " + msg + ": " + e);
			e.printStackTrace();
		}
		SNDPMessage response = null;
		if (msg instanceof IntroductionAcceptedMessage) {
			IntroductionRequest previousIntroduction = null;
			try {
				previousIntroduction = router.getIntroductionRequestForNeighbor(msg.getIntroductionRequest().requester,
						msg.getIntroductionRequest().destination);
			} catch (IOException e) {
				logger.severe(this + ": cannot obtain intro request to "
						+ addrToString(msg.getIntroductionRequest().requester)
						+ ", and so will not be able to send intro complete OR to process feedback.");
			}
			if (logger.isLoggable(FINEST)) {
				logger.finest(this + ": mapping outgoing " + msg.getIntroductionRequest() + " to previous "
						+ previousIntroduction);
			}
			pendingFeedbacksToReceive.put(msg.getIntroductionRequest(),
					new TimeAndIntroductionRequest(eventingSystem.getCurrentTime(), previousIntroduction));
			response = new IntroductionCompletedMessage(msg.getIntroductionRequest(), msg.getSrc());
		} else if (msg instanceof IntroductionRefusedMessage) {
			System.out.println(this + " sending denied because of " + msg);
			response = new IntroductionDeniedMessage(msg.getIntroductionRequest());
		} else {
			System.err.println(
					"Invariant Violation! processTargetResponse should only see Introduction Accepted or Introduction Refused messages.");
			System.err.println("\t---> " + msg);
			System.exit(-1);
		}
		try {
			router.send(response);
		} catch (IOException e) {
			logger.severe(this + " Failed to send " + response + ": " + e);
		}
		// Closing the connection is now being handled in the VPN
//		try {
//			router.closeIntroducedVPN(msg.getIntroductionRequest().requester);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

	protected void processIntroductionRequest(IntroductionRequestMessage m) {
		IntroductionRequest ir = m.getIntroductionRequest();
		if (!ir.requester.equals(m.getSrc())) {
			logger.severe(
					this + ": Invariant Violation. Received introduction request, but ir.requester != m.getSrc().\n"
							+ "\tir=" + ir + ", m=" + m);
			return;
		}
		Pedigree p = getPedigree(ir.requester);
		// System.out.println(this + " received " + ir + ", pedigree=" + p);
		if (reputationModule.reputationIsGreaterThanThreshold(p, verbose)) {
			DiscoveryService.Query query = implementation.getDiscoveryService().createQuery(m.getServerAddress());
			// TODO Create a state machine for handling introduction requests, and add the
			// query to that state machine.
			InetAddress nextHop = implementation.getDiscoveryService().getNextHopTo(query);
			IntroductionOfferMessage offer = //
					new IntroductionOfferMessage(m.getIntroductionRequest(), nextHop, getPedigree(m.getSrc()));
			try {
				router.send(offer);
			} catch (IOException e) {
				System.out.println(this + " denying introduction request " + ir
						+ " because unable to send offer to target " + nextHop);
				logger.severe("Failed to send offer to " + nextHop + ": " + e);
				try {
					router.send(new IntroductionDeniedMessage(m.getIntroductionRequest()));
					// Closing the connection is now being handled in send!
					// router.closeIntroducedVPN(m.getIntroductionRequest().requester);
				} catch (IOException e1) {
					// ignore the failure.
				}
			}
			return;
		}
		// else
		try {
			System.out.println(this + " denying introduction request " + ir + " because of too low of a reputation.");
			router.send(new IntroductionDeniedMessage(m.getIntroductionRequest()));
		} catch (IOException e) {
			// ignore the failure
		}
	}

	@Override
	public String toString() {
		return "Node-" + addrToString(getAddress());
	}

	public EventingSystem getEventingSystem() {
		return eventingSystem;
	}

	public Implementation getImplementation() {
		return implementation;
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
	}

	public Logger getLogger() {
		return logger;
	}

	public void linkClosed(InetAddress neighbor) {
		// System.out.println(this + ": " + vpn + " is closed.");
	}

}
