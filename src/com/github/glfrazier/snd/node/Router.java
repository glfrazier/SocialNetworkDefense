package com.github.glfrazier.snd.node;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.message.AddIntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.protocol.message.WrappedMessage;

public class Router {

	private SNDNode owner;

	private Map<InetAddress, InetAddress> routes;
	private Map<InetAddress, Set<InetAddress>> routeTo;
	private Map<InetAddress, Link> linkMap;

	public Router(SNDNode owner) {
		this.owner = owner;
		routes = new HashMap<>();
		routeTo = new HashMap<>();
		linkMap = Collections.synchronizedMap(new HashMap<>());
	}

	public synchronized void addRoute(InetAddress dst, InetAddress route) {
		routes.put(dst, route);
		Set<InetAddress> destinations = routeTo.get(route);
		if (destinations == null) {
			destinations = new HashSet<>();
			routeTo.put(route, destinations);
		}
		destinations.add(dst);
	}

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

	public boolean canSendTo(InetAddress dst) {
		return getRouteTo(dst) != null;
	}

	private synchronized InetAddress getRouteTo(InetAddress dst) {
		if (linkMap.containsKey(dst)) {
			return dst;
		}
		InetAddress route = routes.get(dst);
		if (route == null) {
			return null;
		}
		return getRouteTo(route);
	}

	public void send(Message msg) throws IOException {
		Link link = linkMap.get(msg.getDst());
		if (link != null) {
			link.send(msg);
			return;
		}
		if (msg instanceof IntroductionMessage || msg instanceof WrappedMessage) {
			throw new IOException(this + ": No VPN available for: " + msg);
		}
		InetAddress rtr = routes.get(msg.getDst());
		if (rtr == null)
			throw new IOException(this + ": No route to " + addrToString(msg.getDst()) + ": trying to send " + msg);
		WrappedMessage wrappedMessage = new WrappedMessage(rtr, owner.getAddress(), msg);
		send(wrappedMessage);
	}

	public synchronized void openLink(InetAddress nbr, Object keyingMaterial) throws IOException {
		if (linkMap.containsKey(nbr)) {
			return;
		}
		Link link = new Link(owner, nbr, keyingMaterial, owner.eventingSystem, linkMap,
				owner.getImplementation().getVpnFactory());
	}

	public synchronized void openIntroducedLink(InetAddress nbr, IntroductionRequest request, Object keyingMaterial)
			throws IOException {
		Link link = linkMap.get(nbr);
		if (link != null) {
			synchronized (link) {
				// Re-check that the vpn is in the VPN_MAP *AFTER* we have synchronized on it.
				if (linkMap.containsKey(nbr)) {
					if (link.addIntroductionRequest(request)) {
						link.send(new AddIntroductionRequestMessage(link.getRemote(), owner.getAddress(), request));
					}
					return;
				}
			}
		}
		link = new Link(owner, nbr, keyingMaterial, request, owner.eventingSystem, linkMap,
				owner.getImplementation().getVpnFactory());
	}

	public boolean closeLink(InetAddress nbr) throws IOException {
		Link link = linkMap.get(nbr);
		if (link == null) {
			return false;
		}
		link.close();
		return true;
	}

	public boolean closeIntroducedVPN(InetAddress nbr, IntroductionRequest ir) throws IOException {
		Link link = linkMap.get(nbr);
		if (link == null) {
			return false;
		}
		return link.close(ir);
	}

	@Override
	public String toString() {
		return "Router for " + owner;
	}

	public synchronized IntroductionRequest getIntroductionRequestForNeighbor(InetAddress nbr, InetAddress destination)
			throws IOException {
		Link link = linkMap.get(nbr);
		if (link == null) {
			throw new IOException(this + " is not connected to " + nbr
					+ " and so does not have an IntroductionRequest for that connection.");
		}
		return link.getIntroductionRequest(destination);
	}

	/**
	 * Returns true if <code>node</code> is an introduced neighbor (see
	 * {@link #openLink}). Returns false if it is a configured neighbor (see
	 * {@link #openLink}) OR if it is no neighbor at all (shouldn't an exception be
	 * thrown in that case?).
	 * 
	 * @param node
	 * @return
	 */
	public boolean isIntroducedNeighbor(InetAddress node) {
		Link link = linkMap.get(node);
		if (link == null) {
			return false;
		}
		return link.isIntroduced();
	}
}
