package com.github.glfrazier.snd.simulation;

import static com.github.glfrazier.snd.simulation.SimVPNFactory.VPN_MAP;
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
import com.github.glfrazier.snd.util.AddressUtils.AddressPair;
import com.github.glfrazier.snd.util.Comms;
import com.github.glfrazier.snd.util.VPN;

public class SimComms implements Comms {

	private SNDNode owner;

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

	// @Override
	private synchronized InetAddress getRouteTo(InetAddress dst) {
		if (VPN_MAP.containsKey(new AddressPair(owner.getAddress(), dst))) {
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
		SimVPNImpl vpn = VPN_MAP.get(new AddressPair(owner.getAddress(), msg.getDst()));
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
		AddressPair key = new AddressPair(owner.getAddress(), nbr);
		if (VPN_MAP.containsKey(key)) {
			return;
		}
		SimVPNImpl vpn = (SimVPNImpl) owner.getImplementation().getVpnFactory().createVPN(nbr, keyingMaterial);
	}

	@Override
	public synchronized void openIntroducedVPN(InetAddress nbr, IntroductionRequest request, Object keyingMaterial)
			throws IOException {
		AddressPair key = new AddressPair(owner.getAddress(), nbr);
		SimVPNImpl vpn = VPN_MAP.get(key);
		if (vpn != null) {
			synchronized (vpn) {
				if (VPN_MAP.containsKey(key)) {
					vpn.addIntroductionRequest(request);
					return;
				}
			}
		}
		vpn = (SimVPNImpl) owner.getImplementation().getVpnFactory().createIntroducedVPN(nbr, request, keyingMaterial);
	}

	@Override
	public boolean closeVPN(InetAddress nbr) throws IOException {
		SimVPNImpl vpn = VPN_MAP.remove(new AddressPair(owner.getAddress(), nbr));
		if (vpn == null) {
			return false;
		}
		vpn.close();
		return true;
	}

	@Override
	public boolean closeIntroducedVPN(InetAddress nbr, IntroductionRequest ir) throws IOException {
		SimVPNImpl vpn = VPN_MAP.get(new AddressPair(owner.getAddress(), nbr));
		if (vpn == null) {
			return false;
		}
		return vpn.close(ir);
	}

	@Override
	public String toString() {
		return "SimComms for " + owner;
	}

	@Override
	public synchronized IntroductionRequest getIntroductionRequestForNeighbor(InetAddress nbr, InetAddress requester,
			InetAddress destination) throws IOException {
		if (!nbr.equals(requester)) {
			new Exception("Why are nbr (" + nbr + ") and requester (" + requester + ") different?").printStackTrace();
		}
		SimVPNImpl vpn = VPN_MAP.get(new AddressPair(owner.getAddress(), nbr));
		if (vpn == null) {
			throw new IOException(this + " is not connected to " + nbr
					+ " and so does not have an IntroductionRequest for that connection.");
		}
		return vpn.getIntroductionRequest(destination);
	}

	@Override
	public boolean isIntroducedNeighbor(InetAddress node) {
		SimVPNImpl vpn = VPN_MAP.get(new AddressPair(owner.getAddress(), node));
		if (vpn == null) {
			return false;
		}
		return vpn.isIntroduced();
	}
}
