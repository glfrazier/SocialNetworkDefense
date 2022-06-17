package com.github.glfrazier.snd.node;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.message.FeedbackMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedWillRouteMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.protocol.message.WrappedMessage;
import com.github.glfrazier.snd.util.Implementation;

public class ServerProxy extends SNDNode {

	private static final Event SERVER_PROXY_MAINTENANCE_EVENT = new Event() {
		private final String name = "ServerProxy Maintenance Event";

		public String toString() {
			return name;
		}
	};
	private InetAddress proxiedAppServer;
	private InetAddress finalIntroducer;
	private Map<InetAddress, TimeAndIntroductionRequest> mostRecentIntroductionRequestForSrc;

	public ServerProxy(InetAddress addr, Implementation impl, EventingSystem es, Properties props) {
		super(addr, impl, es, props);
		mostRecentIntroductionRequestForSrc = new LinkedHashMap<>();
		eventingSystem.scheduleEventRelative(this, SERVER_PROXY_MAINTENANCE_EVENT, FEEDBACK_EXPIRATION_TIME / 10);
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
				LOGGER.severe(this + ": Why did we receive " + m + "?");
				return;
			}
			implementation.getComms().addRoute(enclosed.getSrc(), wrapper.getSrc());
			try {
				IntroductionRequest ir = implementation.getComms().getIntroductionRequestForNeighbor(wrapper.getSrc());
				mostRecentIntroductionRequestForSrc.put(enclosed.getSrc(),
						new TimeAndIntroductionRequest(eventingSystem.getCurrentTime(), ir));
			} catch (IOException e) {
				e.printStackTrace();
				LOGGER.severe(
						this + ": How could we have received a wrapped message but not have a link to the wrapper? msg="
								+ m);
			}
			super.processMessage(wrapper);
			return;
		}
		if (!m.getSrc().equals(proxiedAppServer)) {
			new Exception(this + " should only receive unwrapped messages from our proxiedAppServer: " + m)
					.printStackTrace();
			return;
		}
		// The comms.send() method will automagically wrap and forward messages from the
		// AppServer.
		try {
			implementation.getComms().send(m);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * The ServerProxy will only receive feedback from the app server it is proxying
	 * for. The feedback message will contain a null IntroductionRequest, and the
	 * subject it specifies may itself be a proxied client. The ServerProxy fixes it
	 * all up and send the feedback to its "final" introducer.
	 */
	protected void processFeedback(FeedbackMessage m) {
		if (!m.getSrc().equals(proxiedAppServer)) {
			new Exception(this + " should only receive feedback messages from " + proxiedAppServer + "! : " + m)
					.printStackTrace();
			return;
		}
		IntroductionRequest introductionRequest = m.getIntroductionRequest();
		if (introductionRequest != null) {
			new Exception(this
					+ ": feedback messages from the proxiedAppServer should contain null introduction requests: " + m)
					.printStackTrace();
			System.exit(-1);
		}
		InetAddress src = m.getSubject();
		TimeAndIntroductionRequest tNreq = mostRecentIntroductionRequestForSrc.remove(src);
		if (tNreq == null) {
			LOGGER.warning(
					this + " Received a feedback message about a connection that has expired or never was: " + m);
			return;
		}
		FeedbackMessage fm = new FeedbackMessage(introductionRequest, getAddress(), src, m.getFeedback());
		try {
			implementation.getComms().send(fm);
		} catch (IOException e) {
			LOGGER.severe("Failed transmission: " + e);
		}
	}

	@Override
	public String toString() {
		return "ServerProxy-" + getAddress();
	}

	@Override
	public void process(Event e, EventingSystem eventingSystem) {
		super.process(e, eventingSystem);
		if (e == SERVER_PROXY_MAINTENANCE_EVENT) {
			Thread t = new Thread() {
				public void run() {
					synchronized (mostRecentIntroductionRequestForSrc) {
						long now = eventingSystem.getCurrentTime();
						Iterator<InetAddress> iter = mostRecentIntroductionRequestForSrc.keySet().iterator();
						while (iter.hasNext()) {
							InetAddress src = iter.next();
							TimeAndIntroductionRequest tNir = mostRecentIntroductionRequestForSrc.get(src);
							if (now - tNir.time < FEEDBACK_EXPIRATION_TIME) {
								break;
							}
							iter.remove();
						}
					}
				}
			};
			t.start();
		}
	}

	private static class TimeAndIntroductionRequest {
		public long time;
		public IntroductionRequest ir;

		public TimeAndIntroductionRequest(long t, IntroductionRequest i) {
			time = t;
			ir = i;
		}
	}
}
