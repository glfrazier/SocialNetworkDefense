package com.github.glfrazier.snd.simulation;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;
import java.util.logging.Logger;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.Feedback;
import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.protocol.message.AckMessage;
import com.github.glfrazier.snd.protocol.message.FeedbackMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.simulation.TrafficGenerator.MessageContent;

public class TrafficReceiver implements MessageReceiver {

	protected static final Logger LOGGER = Logger.getLogger(TrafficReceiver.class.getName());
	
	public static final String RESPONSE_TO_BAD_MESSAGE = "This is a response to an attack message.";
	public static final String RESPONSE_TO_GOOD_MESSAGE = "This is a response to a benign message.";
	private float falsePositiveRate;
	private float falseNegativeRate;
	private Simulation sim;
	private Random random = new Random();

	private SimVPN vpnToProxy;
	private InetAddress address;
	private Statistics stats;

	public TrafficReceiver(InetAddress address, float falsePositiveRate, float falseNegativeRate, Simulation sim) {
		this.address = address;
		this.falsePositiveRate = falsePositiveRate;
		this.falseNegativeRate = falseNegativeRate;
		this.sim = sim;
		this.stats = sim.getStats();
	}

	@Override
	public InetAddress getAddress() {
		return address;
	}

	@Override
	public void receive(Message m) {
		if (m instanceof AckMessage) {
			// ignore acks!
			return;
		}
		Message response = null;
		MessageContent content = (MessageContent)m.getContent();
		if (content.isAttack) {
			stats.badMessageReceived();
			if (random.nextFloat() > falseNegativeRate) {
				// It was an attack and we detected the attack
				response = new FeedbackMessage(address, m.getSrc(), Feedback.BAD);
			}
		} else {
			stats.goodMessageReceived();
			if (random.nextFloat() < falsePositiveRate) {
				// It was not an attack, but we thought it was
				response = new FeedbackMessage(address, m.getSrc(), Feedback.BAD);
			}
		}
		if (response == null) {
				response = new Message(m.getSrc(), getAddress(), new MessageContent(content));
		}
		if (sim.verbose) {
			sim.printEvent(this + " received " + m + " and is responding with " + response);
		}
		try {
			vpnToProxy.send(response);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public String toString() {
		return "TrafficReceiver<" + addrToString(address) + ">";
	}

	public void attachToServer(SimVPN vpn) {
		this.vpnToProxy = vpn;
	}

	@Override
	public void vpnClosed(InetAddress nbr) {
		// TODO Auto-generated method stub
		
	}


}
