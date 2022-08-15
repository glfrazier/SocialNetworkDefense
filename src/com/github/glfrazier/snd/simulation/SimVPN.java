package com.github.glfrazier.snd.simulation;

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
import com.github.glfrazier.snd.protocol.SNDPMessageTransmissionProtocol;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.util.AddressUtils.AddressPair;

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
		AddressPair key = new AddressPair(local.getAddress(), remoteAddress);
		Map<AddressPair, SimVPN> vpnMap = sim.getVpnMap();
		synchronized (vpnMap) {
			SimVPN prior = vpnMap.get(key);
			if (prior != null) {
				LOGGER.severe(sim.addTimePrefix(this + ": being created when a duplicate already exists!"));
				throw new IllegalStateException(this + ": being created when a duplicate already exists!");
			}
			vpnMap.put(key, this);
		}
		connect();
	}

	private void connect() {
		if (remote != null) {
			return;
		}
		synchronized (this) {
			remote = sim.getVpnMap().get(new AddressPair(remoteAddress, local.getAddress()));
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
			throw new NotConnectedException(sim.addTimePrefix(this + ": is not yet connected, cannot send " + m));
		}
		eventingSystem.scheduleEventRelative(remote, m, SNDPMessageTransmissionProtocol.TRANSMISSION_LATENCY);
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
		sim.getVpnMap().remove(new AddressPair(local.getAddress(), remoteAddress));
		if (remotelyInvoked) {
			local.vpnClosed(remoteAddress);
		} else {
			if (remote != null) {
				eventingSystem.scheduleEventRelative(remote, REMOTE_CLOSE_VPN_EVENT,
						SNDPMessageTransmissionProtocol.TRANSMISSION_LATENCY);
			}
		}
		closed = true;
		remote = null;
	}

	@Override
	public synchronized void process(Event e, EventingSystem eventingSystem) {
		Message m = null;
		if (e instanceof Message) {
			m = (Message) e;
			if (m.isVerbose()) {
				System.out.println(sim.addTimePrefix(this + ": received " + m + "; closed=" + closed));
			}
		}
		if (closed) {
			LOGGER.info(sim.addTimePrefix(this + ": discarding " + e + " because VPN is closed."));
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
