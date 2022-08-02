package com.github.glfrazier.snd.simulation;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventProcessor;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.node.Node;
import com.github.glfrazier.snd.protocol.message.IntroductionMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.util.AddressUtils.AddressPair;
import com.github.glfrazier.snd.util.CommsModule;

public class SimComms implements CommsModule, MessageReceiver
{

	private Node owner;

	private Map<InetAddress, InetAddress> routes;
	private Map<InetAddress, Set<InetAddress>> routeTo;
	private Map<InetAddress, SimVPN> vpnMap;

	public SimComms(Node owner) {
		this.owner = owner;
		routes = new HashMap<>();
		routeTo = new HashMap<>();
		vpnMap = Collections.synchronizedMap(new HashMap<>());
	}

	@Override
	public synchronized void addRoute(InetAddress dst, InetAddress route) {
		routes.put(dst, route);
		Set<InetAddress> destinations = routeTo.get(route);
		if (destinations == null) {
			destinations = new HashSet<>();
			routeTo.put(route, destinations);
		}
		destinations.add(dst);
	}

	@Override
	public synchronized boolean removeRoute(InetAddress dst, InetAddress route) {
		InetAddress rte = routes.get(dst);
		if (rte == null || !rte.equals(route)) {
			return false;
		}
		routes.remove(dst);
		Set<InetAddress> destinations = routeTo.get(route);
		destinations.remove(dst);
		if (destinations.isEmpty()) {
			routeTo.remove(route);
		}
		return true;
	}

	@Override
	public boolean canSendTo(InetAddress dst) {
		return getRouteTo(dst) != null;
	}

	private synchronized InetAddress getRouteTo(InetAddress dst) {
		if (SimVPNManager.VPN_MAP.containsKey(new AddressPair(owner.getAddress(), dst))) {
			return dst;
		}
		InetAddress route = routes.get(dst);
		if (route == null) {
			return null;
		}
		return getRouteTo(route);
	}

	@Override
	public void send(Message msg) throws IOException {
		send(msg, msg.getDst());
	}

	public synchronized void send(Message msg, InetAddress dst) throws IOException {
		SimVPN vpn = SimVPNManager.VPN_MAP.get(new AddressPair(owner.getAddress(), dst));
		if (vpn != null) {
			vpn.send(msg);
			return;
		}
		if (msg instanceof IntroductionMessage) {
			throw new IOException(this + ": No VPN available for: " + msg);
		}
		if (!msg.getDst().equals(dst)) {
			// We're already routed this message!
			throw new IOException(this + ": No route to " + addrToString(msg.getDst()));
		}
		InetAddress rtr = routes.get(msg.getDst());
		if (rtr == null)
			throw new IOException(this + ": No route to " + addrToString(msg.getDst()));
		send(msg, rtr);
	}

	@Override
	public String toString() {
		return "SimComms for " + owner;
	}

//	@Override
//	public void process(Event e, EventingSystem eventingSystem) {
	public void receive(Message m) {
		if (m.getDst().equals(owner.getAddress())) {
			owner.receive(m);
			return;
		}
		if (canSendTo(m.getDst())) {
			try {
				send(m);
			} catch (IOException e1) {
				owner.getLogger().severe("INVARIANT VIOLATION");
				e1.printStackTrace();
				System.exit(-1);
			}
			return;
		}
		// push the message up to the node, so that a route can be created for it
		owner.receive(m);
	}

	@Override
	public synchronized void removeRoutesVia(InetAddress route) {
		Set<InetAddress> destinations = routeTo.remove(route);
		if (destinations == null) {
			return;
		}
		for (InetAddress dst : destinations) {
			routes.remove(dst);
		}
	}

	@Override
	public InetAddress getAddress() {
		return owner.getAddress();
	}

	@Override
	public void vpnClosed(InetAddress nbr) {
		owner.vpnClosed(nbr);
	}

}
