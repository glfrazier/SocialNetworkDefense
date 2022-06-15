package com.github.glfrazier.snd.node;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Properties;

import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedWillRouteMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.protocol.message.WrappedMessage;
import com.github.glfrazier.snd.simulation.SimComms;
import com.github.glfrazier.snd.util.Implementation;

public class ServerProxy extends SNDNode {

	private InetAddress proxiedAppServer;
	private InetAddress finalIntroducer;

	public ServerProxy(InetAddress addr, Implementation impl, EventingSystem es, Properties props) {
		super(addr, impl, es, props);
	}

	public void connectAppServer(InetAddress app) throws IOException {
		// Note that we are not using the ImplBase class' VPN management logic for the
		// vpn to the user/app.
		if (proxiedAppServer != null) {
			implementation.getComms().closeVPN(proxiedAppServer);
			proxiedAppServer = null;
		}
		implementation.getComms().openVPN(app, null);
		proxiedAppServer = app;
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
	public void connectFinalIntroducer(InetAddress introducer) throws IOException {
		if (finalIntroducer != null) {
			implementation.getComms().closeVPN(finalIntroducer);
			finalIntroducer = null;
		}
		this.finalIntroducer = introducer;
		implementation.getComms().openVPN(introducer, null);
		finalIntroducer = introducer;
	}

	protected void processIntroductionRequest(IntroductionRequestMessage m) {
		if (m.getIntroductionRequest().destination.equals(proxiedAppServer)) {
			// Being asked for an introduction to the app server that this node is proxying
			// for.
			// We have already accepted the introduction to this proxy (that is the VPN this
			// request arrived on). So, we can just deny the introduction request with a
			// response that we will route to the server.
			try {
				implementation.getComms().send(new IntroductionDeniedWillRouteMessage(m.getIntroductionRequest()));
			} catch (IOException e) {
				// ignore the failure
			}
		} else {
			// The server proxy does not perform introductions!
			try {
				System.err.println(this + " received unexpted introduction request: " + m);
				implementation.getComms().send(new IntroductionDeniedMessage(m.getIntroductionRequest()));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	protected void processMessage(Message m) {
		if (m instanceof WrappedMessage) {
			WrappedMessage wrapper = (WrappedMessage) m;
			Message enclosed = wrapper.getEnclosedMessage();
			if (!enclosed.getDst().equals(proxiedAppServer)) {
				new Exception(this + ": Why did we receive " + m + "?").printStackTrace();
				return;
			}
			implementation.getComms().addRoute(enclosed.getSrc(), wrapper.getSrc());
			super.processMessage(wrapper);
			return;
		}
		if (!m.getSrc().equals(proxiedAppServer)) {
			new Exception(this + " should only receive unwrapped messages from our proxiedAppServer: " + m)
					.printStackTrace();
			return;
		}
		// The comms.send() method will automagically wrap and forward messages from the AppServer.
		try {
			implementation.getComms().send(m);
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
