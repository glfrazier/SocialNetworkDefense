package com.github.glfrazier.snd.simulation;

import com.github.glfrazier.snd.node.Node;
import com.github.glfrazier.snd.util.CommsModule;
import com.github.glfrazier.snd.util.DiscoveryService;
import com.github.glfrazier.snd.util.Implementation;
import com.github.glfrazier.snd.util.VPNManager;

/**
 * Binds the Node to the Simulation implementation.
 *
 */
public class SimImpl implements Implementation {

	private DiscoveryService disc;
	private ButterflyNetwork model;
	private int cacheSize;
	private SimVPNManager vpnManager;
	private SimComms comms;
	private final Simulation sim;

	public SimImpl(Simulation sim, ButterflyNetwork topology) {
		this.sim = sim;
		this.model = topology;
		cacheSize = sim.getIntegerProperty("snd.discovery_service.cache_size");
	}

	public void setNode(Node node) {
		comms = new SimComms(sim, node);
		vpnManager = new SimVPNManager(sim, node.getEventingSystem(), comms);
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
