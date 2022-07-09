package com.github.glfrazier.snd.simulation;

import com.github.glfrazier.snd.node.SNDNode;
import com.github.glfrazier.snd.util.DiscoveryService;
import com.github.glfrazier.snd.util.Implementation;
import com.github.glfrazier.snd.util.VPNFactory;

public class SimImpl implements Implementation {

	private DiscoveryService disc;
	private ButterflyNetwork model;
	private int cacheSize;
	private SimVPNFactory vpnFactory;
	private SNDNode node;

	public SimImpl(ButterflyNetwork topology) {
		this.model = topology;
	}
	
	public void setNode(SNDNode node) {
		this.node = node;
	}

	@Override
	public DiscoveryService getDiscoveryService() {
		if (disc == null) {
			disc = new ButterflyDiscoveryService(node.getAddress(), model, cacheSize);
		}
		return disc;
	}

	@Override
	public VPNFactory getVpnFactory() {
		if (vpnFactory == null) {
			SimVPNFactory simVpnFactory = new SimVPNFactory(node.getEventingSystem());
			vpnFactory = simVpnFactory;
		}
		return vpnFactory;
	}

}
