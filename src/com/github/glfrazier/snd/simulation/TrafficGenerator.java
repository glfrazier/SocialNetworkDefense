package com.github.glfrazier.snd.simulation;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventProcessor;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.util.VPN;

public class TrafficGenerator implements MessageReceiver, EventProcessor {

	public static final Event WAKEUP_EVENT = new Event() {
		public String toString() {
			return "Wakeup a traffic generator.";
		}
	};
	private InetAddress address;
	private SimVPNImpl vpnToClient;
	private float exponentialRate;

	private Random random;
	private Simulation sim;

	public TrafficGenerator(InetAddress addr, Simulation sim,
			EventingSystem es) {
		address = addr;
		this.sim = sim;
		exponentialRate = sim.getFloatProperty("snd.sim.client_traffic_exponential");
		long seed = sim.getSeed();
		random = new Random();
		random.setSeed(seed);
		es.scheduleEventRelative(this, WAKEUP_EVENT, getDelayToNextEvent());
	}

	public void attachToProxy(SimVPNImpl vpn) {
		this.vpnToClient = vpn;
	}
	
	private long getDelayToNextEvent() {
		double u = random.nextDouble();
		double x = -Math.log(1 - u) / exponentialRate;
		long delta = (long) (1000 * x);
//		System.out.println("exponentialRate=" + exponentialRate + ", u=" + u + ", x=" + x + ", delta=" + delta);
//		new Exception().printStackTrace(System.out);
		return delta;
	}

	@Override
	public InetAddress getAddress() {
		return address;
	}

	@Override
	public void receive(Message m) {
		if (!m.getDst().equals(address)) {
			System.err.println("Why did " + this + " receive " + m + "!?");
			System.exit(-1);
		}
		// TODO record statistics
		sim.printEvent(this + " received " + m);
	}

	@Override
	public void process(Event e, EventingSystem es) {
		// choose a destination
		InetAddress destination = sim.chooseServer();

		// send a message
		Message msg = new Message(destination, address, "Good Behavior");
		sim.printEvent(this + " sending " + msg);
		try {
			vpnToClient.send(msg);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// record the send in stats

		// schedule the next transmission
		long delta = getDelayToNextEvent();
		System.out.println("\t\tDelay to next transmission: " + ((float)delta/1000.0) + " seconds.");
		es.scheduleEventRelative(this, WAKEUP_EVENT, delta);
	}

	@Override
	public String toString() {
		return "TrafficGenerator<" + address + ">";
	}

	@Override
	public void vpnClosed(VPN vpn) {
		System.err.println(this + ": why did this happen?");
	}

}
