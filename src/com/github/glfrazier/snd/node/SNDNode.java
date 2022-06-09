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
import com.github.glfrazier.snd.util.Communicator;
import com.github.glfrazier.snd.util.DiscoveryService;
import com.github.glfrazier.snd.util.PropertyParser;
import com.github.glfrazier.snd.util.VPN;
import com.github.glfrazier.snd.util.VPNFactory;

public class SNDNode implements Communicator {

	private final InetAddress address;
	protected final VPNFactory vpnFactory;
	private final Map<InetAddress, VPN> longLivedVPNs = new HashMap<>();
	private final Map<IntroductionRequest, VPN> ephemeralVPNs = new HashMap<>();

	private final Map<InetAddress, Pedigree> pedigrees = new HashMap<>();

	private Set<IntroductionRequest> pendingFeedbacksToReceive = Collections.synchronizedSet(new HashSet<>());
	private Map<IntroductionRequest, InetAddress> pendingFeedbacksToSend = Collections.synchronizedMap(new HashMap<>());

	protected final ReputationModule reputationModule;
	protected final EventingSystem eventingSystem;
	protected final DiscoveryService discoveryService;
	private final Properties properties;

	private static final Logger LOGGER = Logger.getLogger(SNDNode.class.getName());

	public SNDNode(InetAddress addr, VPNFactory vpnFactory, DiscoveryService discoveryService,
			EventingSystem eventingSystem, Properties properties) {
		this.properties = properties;
		if (properties == null || properties.isEmpty()) {
			throw new NullPointerException("SNDNode requires properties!");
		}
		this.address = addr;
		this.vpnFactory = vpnFactory;
		this.eventingSystem = eventingSystem;
		this.reputationModule = new ReputationModule(eventingSystem, this);
		this.discoveryService = discoveryService;
		vpnFactory.initialize(this);
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

	public int getIntegerProperty(String propName) {
		return PropertyParser.getIntegerProperty(propName, properties);
	}

	public synchronized Pedigree getPedigree(InetAddress client) {
		Pedigree p = pedigrees.get(client);
		if (p == null) {
			if (!longLivedVPNs.containsKey(client)) {
				System.err.println("Invariant Violation! We do not have a pedigree for " + client
						+ ", but we also do not have a long-lived VPN to them.");
				System.exit(-1);
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
	 * Obtain a long-lived VPN.
	 */
	@Override
	public synchronized VPN openVPN(InetAddress nbr) throws IOException {
		if (longLivedVPNs.containsKey(nbr)) {
			throw new IOException("VPN already exists.");
		}
		VPN vpn = vpnFactory.createVPN(nbr);
		longLivedVPNs.put(nbr, vpn);
		return vpn;
	}

	/**
	 * Obtain an ephemeral VPN.
	 */
	@Override
	public synchronized VPN openVPN(InetAddress nbr, IntroductionRequest request) throws IOException {
		if (ephemeralVPNs.containsKey(request)) {
			throw new IOException("VPN already exists.");
		}
		VPN vpn = vpnFactory.createEphemeralVPN(nbr, request);
		ephemeralVPNs.put(request, vpn);
		return vpn;
	}

	@Override
	public synchronized VPN getVPN(InetAddress nbr) {
		VPN vpn = longLivedVPNs.get(nbr);
		return vpn;
	}

	@Override
	public synchronized VPN getVPN(IntroductionRequest req) {
		return ephemeralVPNs.get(req);
	}

	@Override
	public synchronized void closeVPN(InetAddress nbr) {
		VPN vpn = longLivedVPNs.remove(nbr);
		if (vpn == null) {
			return;
		}
		vpn.close();
	}

	@Override
	public synchronized void closeVPN(IntroductionRequest req) {
		VPN vpn = ephemeralVPNs.remove(req);
		if (vpn == null) {
			return;
		}
		vpn.close();
	}

	public synchronized boolean isEphemeral(VPN vpn) {
		return ephemeralVPNs.containsValue(vpn);
	}

	////////////////////////////////////////////////////////////////////////
	// END section on VPN management. BEGIN section on message processing. /
	////////////////////////////////////////////////////////////////////////

	@Override
	public void receive(Message m, VPN vpn) {
		if (!(m instanceof SNDMessage)) {
			processMessage(m, vpn);
			return;
		}
		SNDMessage msg = (SNDMessage)m;
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
			processIntroductionRequest((IntroductionRequestMessage) m, vpn);
			break;
		case INTRODUCTION_OFFER:
			processIntroductionOffer((IntroductionOfferMessage) m, vpn);
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

	protected void processMessage(Message m, VPN vpn) {
		// ClientImpl and ServerImpl override this method.
		new Exception(this + ": This method should never be invoked!").printStackTrace();
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
		InetAddress introducer = pendingFeedbacksToSend.remove(req);
		if (introducer != null) {
			if (pedigree == null || pedigree.getRequestSequence().length == 0) {
				new Exception(
						"Invariant Violation: we think we should forward this feedback, but there is no previous pedigree.")
						.printStackTrace();
				System.exit(-1);
			}
			VPN vpn = getVPN(introducer);
			if (vpn == null) {
				LOGGER.severe("We have a pending feedback for an introducer we are not connected to.");
			}
			try {
				vpn.send(new FeedbackMessage(m.getIntroductionRequest(), getAddress(), m.getFeedback()));
			} catch (IOException e) {
				LOGGER.severe("VPN " + vpn + " failed transmission: " + e);
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

	protected void processIntroductionOffer(IntroductionOfferMessage m, VPN vpn) {
		Pedigree p = m.getPedigree().getNext(m.getIntroductionRequest());
		if (reputationModule.reputationIsGreaterThanThreshold(p)) {
			// If the client's reputation is above threshold, create a transaction-specific
			// VPN to the client and send an Introduction Accepted message.
			try {
				openVPN(m.getIntroductionRequest().requester, m.getIntroductionRequest());
			} catch (IOException e) {
				LOGGER.severe("Received an IOException when creating a VPN to " + m.getIntroductionRequest().requester
						+ ": " + e);
				try {
					vpn.send(new IntroductionRefusedMessage(m.getIntroductionRequest(), getAddress()));
				} catch (IOException e1) {
					LOGGER.severe("Failed to send message over " + vpn + ": " + e1);
				}
				return;
			}
			try {
				vpn.send(new IntroductionAcceptedMessage(m.getIntroductionRequest(), getAddress()));
			} catch (IOException e) {
				LOGGER.severe("Failed to send message over " + vpn + ": " + e);
			}

			// Add an entry to the pendingFeedbacksToSend, identifying the introducer as the
			// node to send the feedback to
			pendingFeedbacksToSend.put(m.getIntroductionRequest(), vpn.getRemote());

		} else {
			try {
				vpn.send(new IntroductionRefusedMessage(m.getIntroductionRequest(), getAddress()));
			} catch (IOException e) {
				LOGGER.severe("Failed to send message over " + vpn + ": " + e);
			}
		}

	}

	private void processTargetResponse(SNDMessage msg) {
		// Send an Introduction Completed message to the client.
		IntroductionRequest request = msg.getIntroductionRequest();
		Pedigree p = getPedigree(request.requester);
		IntroductionRequest[] seq = p.getRequestSequence();
		VPN vpn = null;
		IntroductionRequest ir = null;
		if (seq.length > 0) {
			ir = seq[seq.length - 1];
			vpn = getVPN(ir);
		}
		boolean isEphemeral = true;
		if (vpn == null) {
			vpn = getVPN(request.requester);
			isEphemeral = false;
		}
		if (vpn == null) {
			LOGGER.severe("Received response to " + request + ", but there is no VPN for " + request.requester);
			return;
		}
		try {
			if (msg instanceof IntroductionAcceptedMessage) {
				vpn.send(new IntroductionCompletedMessage(request, msg.getSrc()));
				pendingFeedbacksToReceive.add(request);
			} else if (msg instanceof IntroductionRefusedMessage) {
				vpn.send(new IntroductionDeniedMessage(request));
			} else {
				System.err.println(
						"Invariant Violation! processTargetResponse should only see Introduction Accepted or Introduction Refused messages.");
				System.err.println("\t---> " + msg);
				System.exit(-1);
			}
		} catch (IOException e) {
			// Log the problem
			LOGGER.severe("Failed to send over VPN " + vpn + ": " + e);
		}
		if (isEphemeral) {
			closeVPN(ir);
		}
	}

	protected void processIntroductionRequest(IntroductionRequestMessage m, VPN vpn) {
		if (reputationModule.reputationIsGreaterThanThreshold(getPedigree(m.getSrc()))) {
			DiscoveryService.Query query = discoveryService.createQuery(m.getServerAddress());
			// TODO Create a state machine for handling introduction requests, and add the
			// query to that state machine.
			InetAddress nextHop = discoveryService.getNextHopTo(query);
			IntroductionOfferMessage offer = //
					new IntroductionOfferMessage(m.getIntroductionRequest(), nextHop, getPedigree(m.getSrc()));
			try {
				getVPN(nextHop).send(offer);
			} catch (IOException e) {
				try {
					vpn.send(new IntroductionDeniedMessage(m.getIntroductionRequest()));
				} catch (IOException e1) {
					// ignore the failure.
				}
			}
			return;
		}
		// else
		try {
			vpn.send(new IntroductionDeniedMessage(m.getIntroductionRequest()));
		} catch (IOException e) {
			// ignore the failure
		}
		if (isEphemeral(vpn)) {
			closeVPN(m.getIntroductionRequest());
		}
	}

	@Override
	public String toString() {
		return "Node-" + getAddress();
	}

}
