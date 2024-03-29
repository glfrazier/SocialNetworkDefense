package com.github.glfrazier.snd.simulation;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.node.Node;
import com.github.glfrazier.snd.protocol.message.IntroductionAcceptedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionOfferMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRefusedMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.protocol.message.SNDPMessage;
import com.github.glfrazier.snd.util.AddressUtils.AddressPair;
import com.github.glfrazier.snd.util.CommsModule;

public class SimComms implements CommsModule, MessageReceiver {

	private final Node owner;

	private Map<InetAddress, InetAddress> routes;
	private Map<InetAddress, Set<InetAddress>> routeTo;

	private final Simulation sim;

	private Map<InetAddress, SimVPN> vpnMap;

	public SimComms(Simulation sim, Node owner) {
		this.sim = sim;
		this.owner = owner;
		this.vpnMap = sim.getVpnMap(owner.getAddress());
		routes = Collections.synchronizedMap(new HashMap<>());
		routeTo = Collections.synchronizedMap(new HashMap<>());
	}

	@Override
	public void addRoute(InetAddress dst, InetAddress route) {
		routes.put(dst, route);
		Set<InetAddress> destinations = null;
		synchronized (routeTo) {
			destinations = routeTo.get(route);
			if (destinations == null) {
				destinations = Collections.synchronizedSet(new HashSet<>());
				routeTo.put(route, destinations);
			}
		}
		destinations.add(dst);
	}

	@Override
	public boolean removeRoute(InetAddress dst, InetAddress route) {
		InetAddress rte = routes.get(dst);
		if (rte == null || !rte.equals(route)) {
			return false;
		}
		routes.remove(dst);
		synchronized (routeTo) {
			Set<InetAddress> destinations = routeTo.get(route);
			synchronized (destinations) {
				destinations.remove(dst);
				if (destinations.isEmpty()) {
					routeTo.remove(route);
				}
			}
		}
		return true;
	}

	@Override
	public boolean canSendTo(InetAddress dst) {
		return getRouteTo(dst) != null;
	}

	private InetAddress getRouteTo(InetAddress dst) {
		if (vpnMap.containsKey(dst)) {
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
		// We send a copy of the message so that the local node can modify/manipulate
		// the message after transmission w/out affecting the message at the
		// destination.
		Message msgCopy = msg;
//		try {
//			ByteArrayOutputStream bout = new ByteArrayOutputStream();
//			ObjectOutputStream oos = new ObjectOutputStream(bout);
//			oos.writeObject(msg);
//			oos.close();
//			ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
//			ObjectInputStream ois = new ObjectInputStream(bin);
//			msgCopy = (Message) ois.readObject();
//		} catch (Exception e) {
//			System.err.println("Sending message: " + msg);
//			e.printStackTrace();
//			System.exit(-1);
//		}
		send(msgCopy, msgCopy.getDst());
	}

	public void send(Message msg, InetAddress dst) throws IOException {
		if (dst.equals(owner.getAddress())) {
			System.err.println("#1 Why are we sending a message to ourselves!? msg=" + msg);
			new Exception().printStackTrace();
			System.exit(-1);
		}
		if (msg.getDst().equals(msg.getSrc())) {
			System.err.println("#2 Why are we sending a message with dst==src!? msg=" + msg);
			new Exception().printStackTrace();
			System.exit(-1);
		}
		if (msg.isVerbose()) {
			System.out.println(sim.addTimePrefix(this + ": sending " + msg + " to " + addrToString(dst)));
		}
		SimVPN vpn = vpnMap.get(dst);
		if (vpn != null) {
			vpn.send(msg);
			if (msg.isVerbose()) {
				System.out.println("\tsent!");
			}
			return;
		}
		if (msg instanceof IntroductionMessage) {
			throw new IOException(
					sim.addTimePrefix(this + ": No VPN available for: " + msg + " being sent to " + addrToString(dst)));
		}
		if (!msg.getDst().equals(dst)) {
			if (msg.isVerbose()) {
				System.out.println("\tit has already been routed, so throw an IOException.");
			}
			// We're already routed this message!
			throw new IOException(sim
					.addTimePrefix(this + ": No route to " + addrToString(msg.getDst()) + ", trying to send " + msg));
		}
		InetAddress rtr = routes.get(msg.getDst());
		if (rtr == null) {
			if (msg.isVerbose()) {
				System.out.println("\tthere is no route, so throw an IOException.");
			}
			throw new IOException(sim
					.addTimePrefix(this + ": No route to " + addrToString(msg.getDst()) + ", trying to send " + msg));
		}
		if (msg.isVerbose()) {
			System.out.println("\ttry sending it again, this time to " + rtr);
		}
		send(msg, rtr);
	}

	@Override
	public String toString() {
		return "SimComms for " + owner;
	}

//	@Override
//	public void process(Event e, EventingSystem eventingSystem) {
	public void receive(Message m) {
		boolean v = m.isVerbose();
		if (v) {
			System.out.println(sim.addTimePrefix(this + ": received " + m));
		}
		if (m.getDst().equals(owner.getAddress())) {
			if (v) {
				System.out.println("\tthis is the destination; pushing " + m + " to the node.");
			}
			owner.receive(m);
			return;
		}
		try {
			if (v) {
				System.out.println("\tinvoking send()");
			}
			send(m);
			if (v) {
				System.out.println("\tWe were able to send " + m);
			}
			return;
		} catch (IOException e1) {
			if (m instanceof SNDPMessage) {
				if (m instanceof IntroductionRefusedMessage || m instanceof IntroductionAcceptedMessage
						|| m instanceof IntroductionOfferMessage) {
					System.err.println(
							"INVARIANT VIOLATION! Messages of this type should only be sent on a-priori links! m=" + m);
					System.exit(-1);
				}
				// We drop SND Protocol messages that cannot be sent
				return;
			}
		}
		// push the message up to the node, so that a route can be created for it
		if (v) {
			System.out.println("\tthere is no route; pusing " + m + " to the node.");
		}
		owner.receive(m);
	}

	@Override
	public void removeRoutesVia(InetAddress route) {
		Set<InetAddress> destinations = routeTo.remove(route);
		if (destinations == null) {
			return;
		}
		synchronized (destinations) {
			for (InetAddress dst : destinations) {
				routes.remove(dst);
			}
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

	@Override
	public void process(Event e, EventingSystem eventingSystem, long t) {
		if (e instanceof Message) {
			receive((Message) e);
		}
	}

}
