package com.github.glfrazier.snd.node;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.protocol.ClientConnectToServerProtocol;
import com.github.glfrazier.snd.protocol.RequesterProtocol;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.message.FeedbackMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.util.AddressUtils.AddressPair;
import com.github.glfrazier.snd.util.DenialReporter;
import com.github.glfrazier.snd.util.Implementation;

public class ProxyNode extends Node {
	private InetAddress proxiedHost;
	private InetAddress initialIntroducer;
	private Map<IntroductionRequest, RequesterProtocol> introductionSequences = new HashMap<>();
	private DenialReporter denialReporter;
	private Map<AddressPair, IntroductionRequest> destinationIntroductionMap = Collections
			.synchronizedMap(new HashMap<>());

	public ProxyNode(InetAddress addr, Implementation impl, EventingSystem es, Properties props,
			DenialReporter denialReporter) {
		super(addr, impl, es, props);
		this.denialReporter = denialReporter;
	}

	public synchronized void connectProxiedHost(InetAddress app, Object keyingMaterial) throws IOException {
		if (proxiedHost != null) {
			closeVPN(proxiedHost);
			proxiedHost = null;
		}
		createVPN(app, keyingMaterial);
		proxiedHost = app;
	}

	/**
	 * Connect this client to an introducer, to be its first introducer into the
	 * network. If the client is already connected to an initial introducer, that
	 * connection is first closed; in this implementation, a client can only have
	 * one initial introducer.
	 * 
	 * @param introducer
	 * @throws IOException
	 */
	public void connectInitialIntroducer(InetAddress introducer, Object keyingMaterial) throws IOException {
		if (initialIntroducer != null) {
			closeVPN(initialIntroducer);
			initialIntroducer = null;
		}
		createVPN(introducer, keyingMaterial);
		initialIntroducer = introducer;
	}

	@Override
	protected void processFeedback(FeedbackMessage m) {
		if (m.getSrc().equals(proxiedHost)) {
			// Our proxied host sent us feedback. It will have no introduction request
			// (because proxied hosts know nothing about introductions). The subject of the
			// feedback is probably another proxied host, but it *could* be a member of the
			// network. We must obtain the introduction request by which we know the proxy
			// for the subject of the feedback, and then construct an appropriate feedback
			// message and send it.

			// Find the network destination, as the proxied host may only know the IP
			// address of the other proxied host in the connection
			InetAddress subject = m.getSubject();
			InetAddress networkSrc = implementation.getDiscoveryService().getProxyFor(subject);

			// Close the VPN to the bad host!
			if (m.getFeedback() == Feedback.BAD) {
				removeAllIntroductionRequestsFromVPN(networkSrc);
			}
			
			// Now get the introduction request
			IntroductionRequest ir = getPendingFeedbackTo(networkSrc, getAddress());
			if (ir == null) {
				logger.warning(this + ": received feedback from proxied host for which there is no pending feedback.");
				return;
			}
			// And build and send the feedback
			FeedbackMessage mOut = new FeedbackMessage(ir, getAddress(), subject, m.getFeedback(), m.getTrigger());
			send(mOut);
			return;
		}
		// else
		
		if (m.getSubject().equals(proxiedHost)) {
			System.out.println(this + ": Eventually, we will want to make the client aware that negative feedback has\n"
					+ "been received. And, if we have multiple clients we are routing,\n"
					+ "we may want to do a mini-reputation system here.");
			return;
		}
		// else
		logger.severe(this + " Received feedback that was neither from nor about its proxied host: " + m);
	}

	@Override
	protected synchronized void processMessage(Message m) {
		if (m.getSrc().equals(proxiedHost)) {
			// The message came from the client that this node is proxying for
			InetAddress networkDestination = implementation.getDiscoveryService().getProxyFor(m.getDst());
			if (verbose) {
				System.out.println(this + ": the proxy for " + m.getDst() + " is " + networkDestination);
			}
			if (networkDestination == null) {
				logger.info(
						this + ": discarding " + m + " because it is not in the network and does not have a proxy.");
				return;
			}
			if (implementation.getComms().canSendTo(networkDestination)) {
				try {
					implementation.getComms().addRoute(m.getDst(), networkDestination);
					implementation.getComms().send(m);
					return;
				} catch (IOException e) {
					// ignore the failure and continue on to creating the connection
				}
			}
			ClientConnectToServerProtocol proto = new ClientConnectToServerProtocol(this, m, networkDestination,
					denialReporter, this.verbose);
			// No need to register a callback, as the protocol handles sending the message.
			// proto.registerCallback(this);
			proto.begin();
			return;
		}
		// else
		super.processMessage(m);

	}

	@Override
	public InetAddress getNextHopTo(InetAddress dst) {
		return initialIntroducer;
	}

	public InetAddress getInitialIntroducer() {
		return initialIntroducer;
	}

//  No need to register a callback, as the protocol handles sending the message.
//	@Override
//	public void stateMachineEnded(StateMachine machine) {
//		ClientConnectToServerProtocol proto = (ClientConnectToServerProtocol) machine;
//		if (!proto.isConnected()) {
//			// TODO do something about a failure
//			return;
//		}
//		Message m = proto.getMessage();
//		try {
//			router.send(m);
//		} catch (IOException e) {
//			// TODO do something about a failure
//			return;
//		}
//	}

	@Override
	public String toString() {
		return "ProxyNode-" + addrToString(getAddress());
	}

	public void registerProtocol(RequesterProtocol intro) {
		introductionSequences.put(intro.getIntroductionRequest(), intro);
	}

	public void addIntroductionToDestination(InetAddress dst, InetAddress src,
			IntroductionRequest introductionRequest) {
		destinationIntroductionMap.put(new AddressPair(dst, src), introductionRequest);
	}

}
