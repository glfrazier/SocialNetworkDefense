package com.github.glfrazier.snd.simulation;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.util.VPN;
import com.github.glfrazier.snd.util.VPNEndpoint;
import com.github.glfrazier.snd.util.VPNFactory;

public class SimVPNFactory implements VPNFactory {

	private VPNEndpoint owner;

	private EventingSystem eventingSystem;

	private static Map<InetAddress, Map<InetAddress, SimVPNImpl>> longLivedVPNMap = new HashMap<>();

	private static Map<InetAddress, Map<IntroductionRequest, SimVPNImpl>> ephemeralVPNMap = new HashMap<>();

	public SimVPNFactory(EventingSystem es) {
		this.eventingSystem = es;
	}

	@Override
	public void initialize(VPNEndpoint owner) {
		this.owner = owner;
	}

	@Override
	public VPN createVPN(InetAddress remote) {
		if (owner == null) {
			throw new IllegalStateException("initialize(owner) has not been invoked");
		}
		return new SimVPNImpl(owner, remote, eventingSystem, longLivedVPNMap);
	}

	@Override
	public VPN createEphemeralVPN(InetAddress remote, IntroductionRequest transaction) {
		if (owner == null) {
			throw new IllegalStateException("initialize(owner) has not been invoked");
		}
		return new SimVPNImpl(owner, remote, transaction, eventingSystem, ephemeralVPNMap);
	}

}
