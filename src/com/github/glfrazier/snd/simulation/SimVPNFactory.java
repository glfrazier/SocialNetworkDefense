package com.github.glfrazier.snd.simulation;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.util.VPN;
import com.github.glfrazier.snd.util.VPNFactory;

public class SimVPNFactory implements VPNFactory {

	private MessageReceiver owner;

	private EventingSystem eventingSystem;

	private static Map<InetAddress, Map<InetAddress, SimVPNImpl>> longLivedVPNMap = new HashMap<>();

	private static Map<InetAddress, Map<IntroductionRequest, SimVPNImpl>> ephemeralVPNMap = new HashMap<>();

	public SimVPNFactory(EventingSystem es) {
		this.eventingSystem = es;
	}

	public void initialize(MessageReceiver owner) {
		this.owner = owner;
	}

	@Override
	public VPN createVPN(InetAddress remote, Object keyingMaterial) throws IOException {
		if (owner == null) {
			throw new IllegalStateException("initialize(owner) has not been invoked");
		}
		return new SimVPNImpl(owner, remote, eventingSystem, longLivedVPNMap);
	}

	@Override
	public VPN createIntroducedVPN(InetAddress remote, IntroductionRequest transaction, Object keyingMaterial)
			throws IOException {
		if (owner == null) {
			throw new IllegalStateException("initialize(owner) has not been invoked");
		}
		return new SimVPNImpl(owner, remote, transaction, eventingSystem, ephemeralVPNMap);
	}

}
