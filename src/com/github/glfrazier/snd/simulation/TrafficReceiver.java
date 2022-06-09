package com.github.glfrazier.snd.simulation;

import java.io.IOException;
import java.net.InetAddress;

import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.protocol.message.ServerAppMessage;
import com.github.glfrazier.snd.util.VPN;
import com.github.glfrazier.snd.util.VPNEndpoint;

public class TrafficReceiver implements VPNEndpoint {

	private float falsePositiveRate;
	private float falseNegativeRate;
	private Simulation sim;

	private VPN vpnToServer;
	private InetAddress address;

	public TrafficReceiver(InetAddress address, float falsePositiveRate, float falseNegativeRate, Simulation sim) {
		this.address = address;
		this.falsePositiveRate = falsePositiveRate;
		this.falseNegativeRate = falseNegativeRate;
		this.sim = sim;
	}

	public void attachToServer(VPN vpn) {
		vpnToServer = vpn;
	}

	@Override
	public InetAddress getAddress() {
		return address;
	}

	@Override
	public void receive(Message m, VPN vpn) {
		ServerAppMessage response = new ServerAppMessage(m.getSrc(), getAddress(), "Response!");
		System.out.println(sim.getCurrentTime() + ":\t" + this + " received " + m + " and is responding with " + response);
		try {
			vpnToServer.send(response);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public String toString() {
		return "TrafficReceiver<" + address + ">";
	}
}
