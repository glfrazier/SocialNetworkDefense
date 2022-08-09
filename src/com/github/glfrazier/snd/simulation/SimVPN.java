package com.github.glfrazier.snd.simulation;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;
import static java.util.logging.Level.FINE;

import java.io.IOException;
import java.net.InetAddress;
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
	private MessageReceiver local;
	private EventingSystem eventingSystem;
	private InetAddress remoteAddress;
	private boolean closed = false;

	public SimVPN(MessageReceiver local, InetAddress remote, EventingSystem eventingSystem) {
		this.local = local;
		this.remoteAddress = remote;
		this.eventingSystem = eventingSystem;
		AddressPair key = new AddressPair(local.getAddress(), remoteAddress);
		SimVPN prior = SimVPNManager.VPN_MAP.get(key);
		if (prior != null) {
			prior.close(false);
			LOGGER.severe(this + ": being created when a duplicate already exists!");
//			new Exception(this + ": being created when a duplicate already exists!").printStackTrace();
//			System.exit(-1);
		}
		SimVPNManager.VPN_MAP.put(key, this);
		connect();
	}

	private void connect() {
		if (remote != null) {
			return;
		}
		synchronized (this) {
			remote = SimVPNManager.VPN_MAP.get(new AddressPair(remoteAddress, local.getAddress()));
			if (remote == null) {
				return;
			}
		}
		remote.connect();
	}

	public synchronized void send(Message m) throws IOException {
		if (remote == null) {
			LOGGER.severe(this + ": not connected to " + addrToString(remoteAddress) + ", cannot send " + m);
			throw new IOException(this + ": not connected to " + addrToString(remoteAddress) + ", cannot send " + m);
		}
		eventingSystem.scheduleEventRelative(remote, m, SNDPMessageTransmissionProtocol.TRANSMISSION_LATENCY);
	}

	private void close(boolean remotelyInvoked) {
		if (closed) {
			return;
		}
		if (LOGGER.isLoggable(FINE)) {
			LOGGER.fine(this + ": being closed.");
		}
		synchronized (this) {
			SimVPNManager.VPN_MAP.remove(new AddressPair(local.getAddress(), remoteAddress));
			if (remotelyInvoked) {
				local.vpnClosed(remoteAddress);
			} else {
				eventingSystem.scheduleEventRelative(remote, REMOTE_CLOSE_VPN_EVENT,
						SNDPMessageTransmissionProtocol.TRANSMISSION_LATENCY);
			}
			closed = true;
			remote = null;
		}
	}

	@Override
	public synchronized void process(Event e, EventingSystem eventingSystem) {
		if (closed) {
			// if (!e.equals(LOCAL_CLOSE_VPN_EVENT) && !e.equals(REMOTE_CLOSE_VPN_EVENT)) {
			LOGGER.warning(this + ": discarding " + e + " because VPN is closed.");
			// }
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
		local.receive((Message) e);
	}

	@Override
	public String toString() {
		return "SimVPN(" + addrToString(local.getAddress()) + " ==> " + addrToString(remoteAddress) + ")";
	}

}
