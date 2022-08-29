package com.github.glfrazier.snd.simulation;

import static com.github.glfrazier.snd.node.Node.TRANSMISSION_LATENCY;
import static com.github.glfrazier.snd.util.AddressUtils.addrToString;
import static java.util.logging.Level.FINE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.logging.Logger;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventProcessor;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.protocol.message.AckMessage;
import com.github.glfrazier.snd.protocol.message.AddIntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.Message;

public class SimVPN implements EventProcessor {

	private static final Logger LOGGER = Logger.getLogger(SimVPN.class.getName());

	static final Event LOCAL_CLOSE_VPN_EVENT = new Event() {
		private static final String NAME = "LOCAL_CLOSE_VPN_EVENT";

		public String toString() {
			return NAME;
		}
	};

	static final Event REMOTE_CLOSE_VPN_EVENT = new Event() {
		private static final String NAME = "REMOTE_CLOSE_VPN_EVENT";

		public String toString() {
			return NAME;
		}
	};

	private SimVPN remote;
	private final MessageReceiver local;
	private final EventingSystem eventingSystem;
	/**
	 * This field has package protection so that it is observable to other
	 * simulation components.
	 */
	final InetAddress remoteAddress;
	private boolean closed = false;

	private final Simulation sim;

	public SimVPN(Simulation sim, MessageReceiver local, InetAddress remote, EventingSystem eventingSystem)
			throws IllegalStateException {
		this.sim = sim;
		this.local = local;
		this.remoteAddress = remote;
		this.eventingSystem = eventingSystem;
		Map<InetAddress, SimVPN> vpnMap = sim.getVpnMap(local.getAddress());
		synchronized (vpnMap) {
			SimVPN prior = vpnMap.get(remoteAddress);
			if (prior != null) {
				LOGGER.severe(sim.addTimePrefix(this + ": being created when a duplicate already exists!"));
				throw new IllegalStateException(this + ": being created when a duplicate already exists!");
			}
			vpnMap.put(remoteAddress, this);
		}
		connect();
	}

	private void connect() {
		if (remote != null) {
			return;
		}
		synchronized (this) {
			remote = sim.getVpnMap(remoteAddress).get(local.getAddress());
			if (remote == null) {
				return;
			}
		}
		remote.connect();
	}

	public synchronized void send(Message m) throws IOException, IllegalStateException {
		if (closed) {
			throw new IOException(sim.addTimePrefix(this + ": closed, cannot send " + m));
		}
		if (remote == null) {
//			throw new NotConnectedException(sim.addTimePrefix(this + ": is not yet connected, cannot send " + m));
			// silently fail!
			return;
		}
		eventingSystem.scheduleEventRelative(remote, m, TRANSMISSION_LATENCY);
	}

	@SuppressWarnings("serial")
	public static class NotConnectedException extends IOException {

		public NotConnectedException(String msg) {
			super(msg);
		}

	}

	private synchronized void close(boolean remotelyInvoked) {
		if (closed) {
			return;
		}
		if (LOGGER.isLoggable(FINE)) {
			LOGGER.fine(sim.addTimePrefix(this + ": being closed."));
		}
		sim.getVpnMap(local.getAddress()).remove(remoteAddress);
		if (remotelyInvoked) {
			local.vpnClosed(remoteAddress);
		} else {
			if (remote != null) {
				eventingSystem.scheduleEventRelative(remote, REMOTE_CLOSE_VPN_EVENT, TRANSMISSION_LATENCY);
			}
		}
		closed = true;
		remote = null;
	}

	@Override
	public synchronized void process(Event e, EventingSystem eventingSystem, long t) {
		Message m = null;
		if (e instanceof Message) {
			m = (Message) e;
			if (m.isVerbose()) {
				System.out.println(sim.addTimePrefix(this + ": received " + m + "; closed=" + closed));
			}
		}
		if (closed) {
			// IntroductionCompletedMessage acknowledgements often arrive after the VPN has
			// closed. So do not bother to log such things. Same goes for AddIntroductionRequestMessage!
			if (!(e instanceof AckMessage || e instanceof AddIntroductionRequestMessage)) {
				LOGGER.info(sim.addTimePrefix(this + ": discarding " + e + " because VPN is closed."));
			}
			if (m != null && m.isVerbose()) {
				System.out.println(sim.addTimePrefix(this + ": discarding " + e + " because VPN is closed."));
			}
			return;
		}
		if (e.equals(LOCAL_CLOSE_VPN_EVENT)) {
			close(false);
			return;
		}
		if (e.equals(REMOTE_CLOSE_VPN_EVENT)) {
			close(true);
			return;
		}
		eventingSystem.scheduleEvent(local, e);
	}

	@Override
	public String toString() {
		return "SimVPN(" + addrToString(local.getAddress()) + " ==> " + addrToString(remoteAddress) + ")";
	}

}
