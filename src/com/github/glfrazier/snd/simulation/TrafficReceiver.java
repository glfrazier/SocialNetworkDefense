package com.github.glfrazier.snd.simulation;

import java.io.IOException;
import java.net.InetAddress;

import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.util.VPN;

public class TrafficReceiver implements MessageReceiver {

	private float falsePositiveRate;
	private float falseNegativeRate;
	private Simulation sim;

	private SimVPNImpl vpnToProxy;
	private InetAddress address;

	public TrafficReceiver(InetAddress address, float falsePositiveRate, float falseNegativeRate, Simulation sim) {
		this.address = address;
		this.falsePositiveRate = falsePositiveRate;
		this.falseNegativeRate = falseNegativeRate;
		this.sim = sim;
	}


	@Override
	public InetAddress getAddress() {
		return address;
	}

	@Override
	public void receive(Message m) {
		Message response = new Message(m.getSrc(), getAddress(), "Response!");
		sim.printEvent(this + " received " + m + " and is responding with " + response);
		try {
			vpnToProxy.send(response);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public String toString() {
		return "TrafficReceiver<" + address + ">";
	}


	public void attachToServer(SimVPNImpl vpn) {
		this.vpnToProxy = vpn;		
	}


	@Override
	public void vpnClosed(VPN vpn) {
		System.err.println(this + ": why did this happen?");
	}
}
