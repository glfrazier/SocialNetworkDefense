package com.github.glfrazier.snd.simulation;

import com.github.glfrazier.snd.node.Node;
import com.github.glfrazier.snd.util.CommsModule;
import com.github.glfrazier.snd.util.DiscoveryService;
import com.github.glfrazier.snd.util.Implementation;
import com.github.glfrazier.snd.util.VPNManager;

public class SimImpl implements Implementation {

	private DiscoveryService disc;
	private ButterflyNetwork model;
	private int cacheSize;
	private SimVPNManager vpnManager;
	private Node node;
	private SimComms comms;

	public SimImpl(ButterflyNetwork topology) {
		this.model = topology;
	}

	public void setNode(Node node) {
		this.node = node;
		comms = new SimComms(node);
		vpnManager = new SimVPNManager(node.getEventingSystem(), comms);
		disc = new ButterflyDiscoveryService(node.getAddress(), model, cacheSize);
	}

	@Override
	public DiscoveryService getDiscoveryService() {
		return disc;
	}

	@Override
	public VPNManager getVPNManager() {
		return vpnManager;
	}

	@Override
	public CommsModule getComms() {
		return comms;
	}

}
