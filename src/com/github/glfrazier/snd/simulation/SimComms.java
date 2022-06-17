package com.github.glfrazier.snd.simulation;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.github.glfrazier.snd.node.SNDNode;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.protocol.message.SNDMessage;
import com.github.glfrazier.snd.protocol.message.WrappedMessage;
import com.github.glfrazier.snd.util.Comms;
import com.github.glfrazier.snd.util.VPN;

public class SimComms implements Comms {

	private SNDNode owner;

	private final Map<InetAddress, SimVPNImpl> longLivedVPNs = new HashMap<>();
	private final Map<InetAddress, SimVPNImpl> introducedVPNs = new HashMap<>();

	private Map<InetAddress, InetAddress> routes;
	private Map<InetAddress, Set<InetAddress>> routeTo;

	public SimComms(SNDNode owner) {
		this.owner = owner;
		routes = new HashMap<>();
		routeTo = new HashMap<>();
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
		SimVPNImpl vpn = longLivedVPNs.get(dst);
		if (vpn != null) {
			return vpn.getRemote();
		}
		vpn = introducedVPNs.get(dst);
		if (vpn != null) {
			return vpn.getRemote();
		}
		InetAddress route = routes.get(dst);
		if (route == null) {
			return null;
		}
		return getRouteTo(route);
	}

	@Override
	public void send(Message msg) throws IOException {
		SimVPNImpl vpn = null;
		synchronized (this) {
			vpn = longLivedVPNs.get(msg.getDst());
			if (vpn == null) {
				vpn = introducedVPNs.get(msg.getDst());
			}
		}
		if (vpn != null) {
			vpn.send(msg);
			return;
		}
		if (msg instanceof SNDMessage || msg instanceof WrappedMessage) {
			throw new IOException("No VPN available for: " + msg);
		}
		InetAddress rtr = routes.get(msg.getDst());
		if (rtr == null)
			throw new IOException("No route to " + msg.getDst());
		WrappedMessage wrappedMessage = new WrappedMessage(rtr, owner.getAddress(), msg);
		send(wrappedMessage);
	}

	@Override
	public synchronized void openVPN(InetAddress nbr, Object keyingMaterial) throws IOException {
		if (longLivedVPNs.containsKey(nbr)) {
			return;
		}
		SimVPNImpl vpn = (SimVPNImpl) owner.getImplementation().getVpnFactory().createVPN(nbr, keyingMaterial);
		longLivedVPNs.put(nbr, vpn);
	}

	@Override
	public synchronized void openIntroducedVPN(InetAddress nbr, IntroductionRequest request, Object keyingMaterial)
			throws IOException {
		if (longLivedVPNs.containsKey(nbr)) {
			System.err.println(this + ": Why were we introduced to a neighbor!?");
			System.err.println("\tnbr=" + nbr + ", request=" + request);
			new Exception().printStackTrace();
			System.exit(-1);
		}
		if (introducedVPNs.containsKey(nbr)) {
			System.err.println(this + ": Why were we re-introduced to an introduced neighbor!?");
			System.err.println("\tvpn=" + introducedVPNs.get(nbr) + ", nbr=" + nbr + ", request=" + request);
			new Exception().printStackTrace();
			System.exit(-1);
		}
		SimVPNImpl vpn = (SimVPNImpl) owner.getImplementation().getVpnFactory().createIntroducedVPN(nbr, request,
				keyingMaterial);
		introducedVPNs.put(nbr, vpn);
	}

	@Override
	public boolean closeVPN(InetAddress nbr) throws IOException {
		SimVPNImpl vpn = null;
		synchronized (this) {
			vpn = longLivedVPNs.remove(nbr);
			if (vpn == null) {
				vpn = introducedVPNs.remove(nbr);
			}
		}
		if (vpn != null) {
			vpn.close();
			return true;
		}
		return false;
	}

	@Override
	public boolean closeIntroducedVPN(InetAddress nbr) throws IOException {
		SimVPNImpl vpn = null;
		synchronized (this) {
			vpn = introducedVPNs.remove(nbr);
		}
		if (vpn != null) {
			vpn.close();
			return true;
		}
		return false;
	}

	@Override
	public String toString() {
		return "SimComms for " + owner;
	}

	@Override
	public synchronized void vpnClosed(VPN v) {
		SimVPNImpl vpn = (SimVPNImpl) v;
		longLivedVPNs.remove(vpn.getRemote());
		introducedVPNs.remove(vpn.getRemote());
	}

	@Override
	public synchronized IntroductionRequest getIntroductionRequestForNeighbor(InetAddress nbr) throws IOException {
		if (longLivedVPNs.containsKey(nbr)) {
			return null;
		}
		if (introducedVPNs.containsKey(nbr)) {
			return introducedVPNs.get(nbr).getIntroductionRequest();
		}
		throw new IOException("Not connected to " + nbr);
	}

	@Override
	public boolean isNonIntroducedNeighbor(InetAddress node) {
		return longLivedVPNs.containsKey(node);
	}
}
