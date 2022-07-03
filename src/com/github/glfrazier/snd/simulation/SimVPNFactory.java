package com.github.glfrazier.snd.simulation;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.util.AddressUtils.AddressPair;
import com.github.glfrazier.snd.util.VPN;
import com.github.glfrazier.snd.util.VPNFactory;

public class SimVPNFactory implements VPNFactory {

	private MessageReceiver owner;

	private EventingSystem eventingSystem;

	static final Map<AddressPair, SimVPNImpl> VPN_MAP = Collections.synchronizedMap(new HashMap<>());

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
		return new SimVPNImpl(owner, remote, eventingSystem);
	}

	@Override
	public VPN createIntroducedVPN(InetAddress remote, IntroductionRequest introductionRequest, Object keyingMaterial)
			throws IOException {
		if (owner == null) {
			throw new IllegalStateException("initialize(owner) has not been invoked");
		}
		return new SimVPNImpl(owner, remote, introductionRequest, eventingSystem);
	}

}
