package com.github.glfrazier.snd.simulation;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;
import java.util.logging.Logger;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventProcessor;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.node.SNDNode;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.util.VPN;

public class TrafficGenerator implements MessageReceiver, EventProcessor {

	public static final Event WAKEUP_EVENT = new Event() {
		public String toString() {
			return "Wakeup a traffic generator.";
		}
	};

	protected static final Logger LOGGER = Logger.getLogger(TrafficGenerator.class.getName());

	public static final String BENIGN_CONTENT = "Benign Content";
	public static final String ATTACK_CONTENT = "Attack Content";

	private InetAddress address;
	private SimVPNImpl vpnToClient;
	private float exponentialRate;

	private Random random;
	private Simulation sim;
	private Statistics stats;
	private long endTime;

	private boolean isAttacker;

	public TrafficGenerator(InetAddress addr, Simulation sim, EventingSystem es) {
		address = addr;
		this.sim = sim;
		this.stats = sim.getStats();
		this.endTime = sim.getEndTime();
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
		if (TrafficReceiver.RESPONSE_TO_BAD_MESSAGE.equals(m.getContent())) {
			stats.responseToBadMessageReceived();
		} else {
			stats.responseToGoodMessageReceived();
		}
		if (sim.verbose) {
			sim.printEvent(this + " received " + m);
		}
	}

	@Override
	public void process(Event e, EventingSystem es) {

		if (endTime - es.getCurrentTime() < 1000) {
			// Do not initiate a new message within one second of the end of the simulation.
			// The message disposition statistics all sum up if every message has reached
			// its destination or been rejected.
			return;
		}
		// choose a destination
		Simulation.MessageMetaData mmd = sim.getNextMessageToSend(this);

		String content = null;
		if (mmd.isAttack) {
			content = ATTACK_CONTENT;
			stats.badMessageSent();
		} else {
			content = BENIGN_CONTENT;
			stats.goodMessageSent();
		}

		// send a message
		Message msg = new Message(mmd.destination, address, content);
		if (sim.verbose) {
			sim.printEvent(this + " sending " + msg);
		}
		try {
			vpnToClient.send(msg);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// record the send in stats

		// schedule the next transmission
		long delta = getDelayToNextEvent();
		if (sim.verbose) {
			System.out.println("\t\tDelay to next transmission: " + ((float) delta / 1000.0) + " seconds.");
		}
		es.scheduleEventRelative(this, WAKEUP_EVENT, delta);
	}

	@Override
	public String toString() {
		return "TrafficGenerator<" + addrToString(address) + ">";
	}

	@Override
	public void vpnClosed(VPN vpn) {
		System.err.println(this + ": why did this happen?");
	}

	public boolean isAttacker() {
		return isAttacker;
	}

	public void setAttacker() {
		isAttacker = true;
	}

	@Override
	public Logger getLogger() {
		return LOGGER;
	}

}
