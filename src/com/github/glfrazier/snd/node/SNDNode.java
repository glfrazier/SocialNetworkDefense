package com.github.glfrazier.snd.node;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.Pedigree;
import com.github.glfrazier.snd.protocol.message.FeedbackMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionAcceptedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionCompletedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedWillRouteMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionOfferMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRefusedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.protocol.message.SNDMessage;
import com.github.glfrazier.snd.protocol.message.WrappedMessage;
import com.github.glfrazier.snd.util.DiscoveryService;
import com.github.glfrazier.snd.util.Implementation;
import com.github.glfrazier.snd.util.PropertyParser;
import com.github.glfrazier.snd.util.VPN;

/**
 * A node in the Social Network Defense network. Implements the SND protocol for
 * establishing VPNs between trusted entities.
 */
public class SNDNode implements MessageReceiver {

	private final InetAddress address;

	private final Map<InetAddress, Pedigree> pedigrees = new HashMap<>();

	/**
	 * The set of introduction requests that we have offered and had accepted, but
	 * not yet received feedback about.
	 */
	private Set<IntroductionRequest> pendingFeedbacksToReceive = Collections.synchronizedSet(new HashSet<>());

	/**
	 * Maps a Client(Proxy) to the IntroductionRequest by which we know that client.
	 * So, when we receive feedback about that client, we can construct our own
	 * feedback message to send.
	 */
	private Map<InetAddress, IntroductionRequest> pendingFeedbacksToSend = Collections.synchronizedMap(new HashMap<>());

	protected final ReputationModule reputationModule;
	protected final EventingSystem eventingSystem;
	protected final Implementation implementation;
	private final Properties properties;

	protected boolean verbose;

	private static final Logger LOGGER = Logger.getLogger(SNDNode.class.getName());

	public SNDNode(InetAddress addr, Implementation implementation, EventingSystem eventingSystem,
			Properties properties) {
		this.properties = properties;
		if (properties == null || properties.isEmpty()) {
			throw new NullPointerException("SNDNode requires properties!");
		}
		this.address = addr;
		this.eventingSystem = eventingSystem;
		this.reputationModule = new ReputationModule(eventingSystem, this);
		this.implementation = implementation;
		this.verbose = getBooleanProperty("snd.node.verbose", false);
	}

	@Override
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
	
	public boolean getBooleanProperty(String propName, boolean defaultValue) {
		return PropertyParser.getBooleanProperty(propName, defaultValue, properties);
	}

	public int getIntegerProperty(String propName) {
		return PropertyParser.getIntegerProperty(propName, properties);
	}

	public synchronized Pedigree getPedigree(InetAddress client) {
		Pedigree p = pedigrees.get(client);
		if (p == null) {
			// This code is simply defensive coding, and it violates the new architecture
//			if (!longLivedVPNs.containsKey(client)) {
//				System.err.println("Invariant Violation! We do not have a pedigree for " + client
//						+ ", but we also do not have a long-lived VPN to them.");
//				System.exit(-1);
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
	 * application-layer Message is received.
	 * 
	 * @param m
	 */
	@Override
	public void receive(Message m) {
		if (!(m instanceof SNDMessage)) {
			processMessage(m);
			return;
		}
		SNDMessage msg = (SNDMessage) m;
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
			processTargetResponse((SNDMessage) m);
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
				implementation.getComms().send(enclosed);
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
		IntroductionRequest req = m.getIntroductionRequest();
		if (!pendingFeedbacksToReceive.contains(req)) {
			LOGGER.severe("Received feedback for a transaction that is not pending feedback.");
			return;
		}
		Pedigree pedigree = getPedigree(req.requester);
		if (pedigree == null) {
			LOGGER.warning(this + " received feedback for a client we no longer know.");
			return;
		}
		reputationModule.applyFeedback(pedigree, m.getFeedback());
		IntroductionRequest previousIntroduction = pendingFeedbacksToSend.remove(req.requester);
		if (previousIntroduction != null) {
			if (pedigree == null || pedigree.getRequestSequence().length == 0) {
				new Exception(
						"Invariant Violation: we think we should forward this feedback, but there is no previous pedigree.")
						.printStackTrace();
				System.exit(-1);
			}
			try {
				implementation.getComms()
						.send(new FeedbackMessage(previousIntroduction, getAddress(), m.getFeedback()));
			} catch (IOException e) {
				LOGGER.severe("Failed transmission: " + e);
			}
		} else {
			if (pedigree.getRequestSequence().length != 0) {
				new Exception(
						"Invariant Violation: we do not have an introducer for this transaction in the pendingFeedbacksToSend, but there are one or more introduces in the pedigree.")
						.printStackTrace();
				System.exit(-1);
			}
		}
	}

	protected void processIntroductionOffer(IntroductionOfferMessage m) {
		Pedigree p = m.getPedigree().getNext(m.getIntroductionRequest());
		if (reputationModule.reputationIsGreaterThanThreshold(p)) {
			// If the client's reputation is above threshold, create a transaction-specific
			// VPN to the client and send an Introduction Accepted message.
			try {
				implementation.getComms().openIntroducedVPN(m.getIntroductionRequest().requester,
						m.getIntroductionRequest(), m.getKeyingMaterial());
				implementation.getComms()
						.send(new IntroductionAcceptedMessage(m.getIntroductionRequest(), getAddress()));
				// Add an entry to the pendingFeedbacksToSend, identifying the introducer as the
				// node to send the feedback to
				pendingFeedbacksToSend.put(m.getIntroductionRequest().requester, m.getIntroductionRequest());
			} catch (IOException e) {
				LOGGER.severe("Failed to send message: " + e);
			}
		} else {
			try {
				implementation.getComms()
						.send(new IntroductionRefusedMessage(m.getIntroductionRequest(), getAddress()));
			} catch (IOException e) {
				LOGGER.severe("Failed to send message: " + e);
			}
		}

	}

	private void processTargetResponse(SNDMessage msg) {
		try {
			if (msg instanceof IntroductionAcceptedMessage) {
				implementation.getComms()
						.send(new IntroductionCompletedMessage(msg.getIntroductionRequest(), msg.getSrc()));
				pendingFeedbacksToReceive.add(msg.getIntroductionRequest());
			} else if (msg instanceof IntroductionRefusedMessage) {
				implementation.getComms().send(new IntroductionDeniedMessage(msg.getIntroductionRequest()));
			} else {
				System.err.println(
						"Invariant Violation! processTargetResponse should only see Introduction Accepted or Introduction Refused messages.");
				System.err.println("\t---> " + msg);
				System.exit(-1);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			implementation.getComms().closeIntroducedVPN(msg.getIntroductionRequest().requester);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void processIntroductionRequest(IntroductionRequestMessage m) {
		if (reputationModule.reputationIsGreaterThanThreshold(getPedigree(m.getSrc()))) {
			DiscoveryService.Query query = implementation.getDiscoveryService().createQuery(m.getServerAddress());
			// TODO Create a state machine for handling introduction requests, and add the
			// query to that state machine.
			InetAddress nextHop = implementation.getDiscoveryService().getNextHopTo(query);
			IntroductionOfferMessage offer = //
					new IntroductionOfferMessage(m.getIntroductionRequest(), nextHop, getPedigree(m.getSrc()));
			try {
				implementation.getComms().send(offer);
			} catch (IOException e) {
				try {
					implementation.getComms().send(new IntroductionDeniedMessage(m.getIntroductionRequest()));
				} catch (IOException e1) {
					// ignore the failure.
				}
			}
			return;
		}
		// else
		try {
			implementation.getComms().send(new IntroductionDeniedMessage(m.getIntroductionRequest()));
		} catch (IOException e) {
			// ignore the failure
		}
		try {
			implementation.getComms().closeIntroducedVPN(m.getIntroductionRequest().requester);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public String toString() {
		return "Node-" + getAddress();
	}

	public EventingSystem getEventingSystem() {
		return eventingSystem;
	}

	public Implementation getImplementation() {
		return implementation;
	}

	@Override
	public void vpnClosed(VPN vpn) {
		implementation.getComms().vpnClosed(vpn);
	}

}
