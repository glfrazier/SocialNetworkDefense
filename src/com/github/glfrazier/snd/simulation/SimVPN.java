package com.github.glfrazier.snd.simulation;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;
import static java.util.logging.Level.FINE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.logging.Logger;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventProcessor;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.Link;
import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.protocol.message.AckMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.util.AddressUtils.AddressPair;
import com.github.glfrazier.snd.util.VPN;

public class SimVPN implements VPN, EventProcessor {

	private static final Logger LOGGER = Logger.getLogger(SimVPN.class.getName());

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
		SimVPN prior = SimVPNFactory.VPN_MAP.get(key);
		if (prior != null) {
			prior.close();
			LOGGER.warning(this + ": being created when a duplicate already exists!");
//			new Exception(this + ": being created when a duplicate already exists!").printStackTrace();
//			System.exit(-1);
		}
		SimVPNFactory.VPN_MAP.put(key, this);
		connect();
	}

	private void connect() {
		if (remote != null) {
			return;
		}
		synchronized (this) {
			remote = SimVPNFactory.VPN_MAP.get(new AddressPair(remoteAddress, local.getAddress()));
			if (remote == null) {
				return;
			}
		}
		remote.connect();
		local.vpnOpened(this);
	}

	@Override
	public synchronized void send(Message m) throws IOException {
		if (remote == null) {
			LOGGER.severe(this + ": not connected to " + addrToString(remoteAddress));
			throw new IOException(this + ": not connected to " + addrToString(remoteAddress));
		}
		eventingSystem.scheduleEventRelative(remote, m, Link.TRANSMISSION_LATENCY);
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		if (LOGGER.isLoggable(FINE)) {
			LOGGER.fine(this + ": being closed.");
		}
		SimVPN r = null;
		synchronized (this) {
			r = remote;
			SimVPNFactory.VPN_MAP.remove(new AddressPair(local.getAddress(), remoteAddress));
			closed = true;
			remote = null;
		}
		local.vpnClosed(this);
		if (r != null) {
			r.close();
		}
	}

	@Override
	public synchronized void process(Event e, EventingSystem eventingSystem) {
		if (closed) {
			if (!(e instanceof AckMessage)) {
				// Don't warn about ACKs
				LOGGER.warning(this + ": discarding " + e + " because VPN is closed.");
			}
			return;
		}
		local.receive((Message) e);
	}

	@Override
	public String toString() {
		return "SimVPN(" + local + ")";
	}

}
