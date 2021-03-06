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
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.RequestProtocol;
import com.github.glfrazier.snd.protocol.message.AckMessage;
import com.github.glfrazier.snd.protocol.message.FeedbackMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.protocol.message.IntroductionMessage;
import com.github.glfrazier.snd.protocol.message.WrappedMessage;
import com.github.glfrazier.snd.util.AddressUtils;
import com.github.glfrazier.snd.util.AddressUtils.AddressPair;
import com.github.glfrazier.snd.util.DenialReporter;
import com.github.glfrazier.snd.util.Implementation;

public class ClientProxy extends SNDNode // implements StateMachineTracker
{
	private InetAddress proxiedAppClient;
	private InetAddress initialIntroducer;
	private Map<IntroductionRequest, RequestProtocol> introductionSequences = new HashMap<>();
	private DenialReporter denialReporter;
	private Map<AddressPair, IntroductionRequest> destinationIntroductionMap = Collections
			.synchronizedMap(new HashMap<>());

	public ClientProxy(InetAddress addr, Implementation impl, EventingSystem es, Properties props,
			DenialReporter denialReporter) {
		super(addr, impl, es, props);
		this.denialReporter = denialReporter;
	}

	public synchronized void connectAppClient(InetAddress app) throws IOException {
		if (proxiedAppClient != null) {
			proxiedAppClient = null;
			router.closeLink(proxiedAppClient);
		}
		try {
			router.openLink(app, null);
			proxiedAppClient = app;
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	/**
	 * Connect this client to an introducer, to be its first introducer into the
	 * network. If the client is already connected to an initial introducer, that
	 * connection is first closed; in this implementation, a client can only have
	 * one initial introducer.
	 * 
	 * @param introducer
	 */
	public void connectInitialIntroducer(InetAddress introducer) {
		if (initialIntroducer != null) {
			try {
				router.closeLink(initialIntroducer);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		this.initialIntroducer = introducer;
		try {
			router.openLink(introducer, null);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	@Override
	public void receive(Message m) {
		if (!(m instanceof IntroductionMessage)) {
			processMessage(m);
			return;
		}
		try {
			router.send(new AckMessage(m.getSrc(), getAddress()));
		} catch (IOException e1) {
			// Ignore a failed ack.
			e1.printStackTrace();
		}
		if (m instanceof FeedbackMessage) {
			processFeedback((FeedbackMessage) m);
			return;
		}
		IntroductionMessage msg = (IntroductionMessage) m;
		RequestProtocol proto = introductionSequences.get(msg.getIntroductionRequest());
		if (proto == null) {
			throw new IllegalStateException("Received an SND message for a protocol that does not exist!");
		}
		proto.receive(msg);
	}

	@Override
	protected void processFeedback(FeedbackMessage m) {
		try {
			router.send(new AckMessage(m.getSrc(), getAddress()));
		} catch (IOException e1) {
			// Ignore a failed ack.
			e1.printStackTrace();
		}
//		System.out.println("Eventually, we will want to make the client aware that negative feedback has\n"
//				+ "been received. And, if we have multiple clients we are routing,\n"
//				+ "we may want to do a mini-reputation system here.");
	}

	@Override
	protected synchronized void processMessage(Message m) {
		if (m instanceof WrappedMessage) {
			Message enc = ((WrappedMessage) m).getEnclosedMessage();
			if (enc.getDst().equals(proxiedAppClient)) {
				// TODO This is probably the wrong place to determine when to close an ephemeral
				// VPN. And we certainly should not do so simply because we sent a Message
				// (packet?) to the application client.
				try {
					IntroductionRequest ir = destinationIntroductionMap.get(new AddressUtils.AddressPair(enc.getSrc(), enc.getDst()));
					router.closeIntroducedVPN(m.getSrc(), ir);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				try {
					router.send(enc);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {

			}
		} else if (m.getSrc().equals(proxiedAppClient)) {
			// The message came from the client that this node is proxying for
			ClientConnectToServerProtocol proto = new ClientConnectToServerProtocol(this, m, denialReporter,
					this.verbose);
			// No need to register a callback, as the protocol handles sending the message.
			// proto.registerCallback(this);
			proto.begin();
			return;
		} else {
			new Exception("We should not be here!").printStackTrace();
		}

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
		return "ClientProxy-" + addrToString(getAddress());
	}

	public void registerProtocol(RequestProtocol intro) {
		introductionSequences.put(intro.getIntroductionRequest(), intro);
	}

	public void addIntroductionToDestination(InetAddress dst, InetAddress src, IntroductionRequest introductionRequest) {
		destinationIntroductionMap.put(new AddressPair(dst, src), introductionRequest);
	}

}
