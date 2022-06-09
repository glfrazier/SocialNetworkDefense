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
import com.github.glfrazier.snd.protocol.message.ClientAppMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.protocol.message.Message.MessageType;
import com.github.glfrazier.snd.protocol.message.SNDMessage;
import com.github.glfrazier.snd.protocol.message.ServerAppMessage;
import com.github.glfrazier.snd.util.VPN;
import com.github.glfrazier.snd.util.VPNEndpoint;
import com.github.glfrazier.snd.util.VPNFactory;
import com.github.glfrazier.statemachine.StateMachine;
import com.github.glfrazier.statemachine.StateMachine.StateMachineTracker;

public class ClientProxy extends SNDNode //
		implements VPNEndpoint, StateMachineTracker {

	private VPN vpnToUser;
	private InetAddress initialIntroducer;
	private Map<IntroductionRequest, RequestProtocol> introductionSequences = new HashMap<>();

	public ClientProxy(InetAddress addr, VPNFactory vpnFactory, EventingSystem es, Properties props) {
		super(addr, vpnFactory, null, // clients do not need discovery
				es, props);
	}

	public void connectAppClient(InetAddress app) {
		// Note that we are not using the ImplBase class' VPN management logic for the
		// vpn to the user/app.
		if (vpnToUser != null) {
			vpnToUser.close();
		}
		vpnToUser = vpnFactory.createVPN(app);
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
			closeVPN(initialIntroducer);
		}
		this.initialIntroducer = introducer;
		try {
			// Access the superclass' implementation of openVPN, avoiding our local
			// implementation.
			super.openVPN(introducer);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	@Override
	public synchronized void receive(Message m, VPN vpn) {
		if (vpnToUser.equals(vpn)) {
			if (!(m instanceof ClientAppMessage)) {
				System.err.println(this + " only expect to receive ClientAppMessages on from the user!");
				System.exit(-1);
			}
			ClientAppMessage appMsg = (ClientAppMessage) m;
			ClientConnectToServerProtocol proto = new ClientConnectToServerProtocol(this, appMsg);
			proto.registerCallback(this);
			proto.begin();
			return;
		}
		if (m.getType() == MessageType.SERVER_TO_CLIENT) {
			processServerResponse((ServerAppMessage) m, vpn);
			return;
		}
		if (m instanceof SNDMessage) {
			SNDMessage ntdm = (SNDMessage) m;
			RequestProtocol proto = introductionSequences.get(ntdm.getIntroductionRequest());
			if (proto == null) {
				throw new IllegalStateException("Received a message for a protocol that does not exist!");
			}
			proto.receive(ntdm);
			return;
		}
		throw new IllegalArgumentException("Received " + m + " on VPN " + vpn + ": what does it mean!?");
	}

	private void processServerResponse(ServerAppMessage m, VPN vpn) {
		try {
			vpnToUser.send(m);
		} catch (IOException e) {
			System.err.println("Transmission to userApp should never fail!");
			e.printStackTrace();
		}
		// TODO This is the wrong place to determine when a transaction is completed.
		// There should be a CLOSE message from the AppClient or the AppServer.
		closeVPN(vpn.getRemote());
	}

	public InetAddress getInitialIntroducer() {
		return initialIntroducer;
	}

	@Override
	public void stateMachineEnded(StateMachine machine) {
		ClientConnectToServerProtocol proto = (ClientConnectToServerProtocol) machine;
		if (!proto.isConnected()) {
			// TODO do something about a failure
			return;
		}
		VPN vpn = proto.getServerVPN();
		ClientAppMessage m = proto.getMessage();
		try {
			vpn.send(m);
		} catch (IOException e) {
			// TODO do something about a failure
			return;
		}
	}
	
	@Override
	public String toString() {
		return "ClientProxy-" + getAddress();
	}

	public void registerProtocol(RequestProtocol intro) {
		introductionSequences.put(intro.getIntroductionRequest(), intro);
	}

}
