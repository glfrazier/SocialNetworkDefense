package com.github.glfrazier.snd.node;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Properties;

import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedWillRouteMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.util.Router;
import com.github.glfrazier.snd.util.VPN;
import com.github.glfrazier.snd.util.VPNFactory;

public class ServerProxy extends SNDNode {

	private VPN vpnToAppServer;
	private InetAddress finalIntroducer;
	private Router router;

	public ServerProxy(InetAddress addr, VPNFactory vpnFactory, EventingSystem es, Properties props) {
		super(addr, vpnFactory, //
				null, // This is the DiscoveryServer argument. Servers do not do discovery.
				es, props);
		router = new Router(this);
	}

	public void connectAppServer(InetAddress app) {
		// Note that we are not using the ImplBase class' VPN management logic for the
		// vpn to the user/app.
		if (vpnToAppServer != null) {
			vpnToAppServer.close();
		}
		vpnToAppServer = vpnFactory.createVPN(app);
	}

	/**
	 * Connect this client to an introducer, to be its first introducer into the
	 * network. If the client is already connected to an initial introducer, that
	 * connection is first closed; in this implementation, a client can only have
	 * one initial introducer.
	 * 
	 * @param introducer
	 */
	public void connectFinalIntroducer(InetAddress introducer) {
		if (finalIntroducer != null) {
			closeVPN(finalIntroducer);
		}
		this.finalIntroducer = introducer;
		try {
			// Access the superclass' implementation of openVPN, avoiding our local
			// implementation.
			super.openVPN(introducer);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	protected void processIntroductionRequest(IntroductionRequestMessage m, VPN vpn) {
		if (m.getDst().equals(vpnToAppServer.getRemote())) {
			// Being asked for an introduction to the app server that this node is proxying
			// for.
			// We have already accepted the introduction to this proxy (that is the VPN this
			// request arrived on). So, we can just deny the introduction request with a
			// response that we will route to the server.
			try {
				vpn.send(new IntroductionDeniedWillRouteMessage(m.getIntroductionRequest()));
			} catch (IOException e) {
				// ignore the failure
			}
		} else {
			// The server proxy does not perform introductions!
			try {
				vpn.send(new IntroductionDeniedMessage(m.getIntroductionRequest()));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	protected void processMessage(Message m, VPN vpn) {
		if (vpn == vpnToAppServer) {
			processServerToClient(m, vpn);
		} else {
			processClientToServer(m, vpn);
		}
	}

	protected void processClientToServer(Message m, VPN vpn) {
		if (!vpnToAppServer.getRemote().equals(m.getDst())) {
			// Why are we preventing the app server from routing the message (packet)
			// onward?
			System.err.println(this + " received a ClientApp msg addressed to " + m.getDst() + ", but our appServer is "
					+ vpnToAppServer.getRemote());
			System.exit(-1);
		}
		try {
			vpnToAppServer.send(m);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		router.addRoute(m.getSrc(), vpn);
	}

	protected void processServerToClient(Message m, VPN vpnIn) {
		if (vpnIn != vpnToAppServer) {
			System.err.println("Invariant Violation! Received a ServerAppMessage on VPN " + vpnIn);
			new Exception().printStackTrace();
			System.exit(-1);
		}
		VPN vpnOut = router.getRouteTo(m.getDst());
		if (vpnOut == null) {
			System.err.println("We do not have a VPN to forward " + m + " onto.");
			return;
		}
		try {
			vpnOut.send(m);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public String toString() {
		return "ServerProxy-" + getAddress();
	}
}
