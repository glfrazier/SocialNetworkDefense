package com.github.glfrazier.snd.node;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.protocol.ClientConnectToServerProtocol;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.RequestProtocol;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.protocol.message.SNDMessage;
import com.github.glfrazier.snd.protocol.message.WrappedMessage;
import com.github.glfrazier.snd.util.Implementation;

public class ClientProxy extends SNDNode //implements StateMachineTracker
		
{

	private InetAddress proxiedAppClient;
	private InetAddress initialIntroducer;
	private Map<IntroductionRequest, RequestProtocol> introductionSequences = new HashMap<>();

	public ClientProxy(InetAddress addr, Implementation impl, EventingSystem es, Properties props) {
		super(addr, impl, es, props);
	}

	public synchronized void connectAppClient(InetAddress app) throws IOException {
		if (proxiedAppClient != null) {
			proxiedAppClient = null;
			implementation.getComms().closeVPN(proxiedAppClient);
		}
		try {
			implementation.getComms().openVPN(app, null);
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
				implementation.getComms().closeVPN(initialIntroducer);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		this.initialIntroducer = introducer;
		try {
			implementation.getComms().openVPN(introducer, null);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	@Override
	public void receive(Message m) {
		if (!(m instanceof SNDMessage)) {
			processMessage(m);
			return;
		}
		SNDMessage msg = (SNDMessage) m;
		RequestProtocol proto = introductionSequences.get(msg.getIntroductionRequest());
		if (proto == null) {
			throw new IllegalStateException("Received an SND message for a protocol that does not exist!");
		}
		proto.receive(msg);
	}

	@Override
	public synchronized void processMessage(Message m) {
		if (m instanceof WrappedMessage) {
			Message enc = ((WrappedMessage) m).getEnclosedMessage();
			if (enc.getDst().equals(proxiedAppClient)) {
				// TODO This is probably the wrong place to determine when to close an ephemeral
				// VPN. And we certainly should not do so simply because we sent a Message
				// (packet?) to the application client.
				try {
					System.out.println(this + " closing connection to " + m.getSrc());
					implementation.getComms().closeIntroducedVPN(m.getSrc());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				try {
					implementation.getComms().send(enc);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {

			}
		} else if (m.getSrc().equals(proxiedAppClient)) {
			// The message came from the client that this node is proxying for
			ClientConnectToServerProtocol proto = new ClientConnectToServerProtocol(this, m, this.verbose);
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
//			implementation.getComms().send(m);
//		} catch (IOException e) {
//			// TODO do something about a failure
//			return;
//		}
//	}

	@Override
	public String toString() {
		return "ClientProxy-" + getAddress();
	}

	public void registerProtocol(RequestProtocol intro) {
		introductionSequences.put(intro.getIntroductionRequest(), intro);
	}

}
