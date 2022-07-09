package com.github.glfrazier.snd.simulation;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.util.AddressUtils.AddressPair;
import com.github.glfrazier.snd.util.VPN;
import com.github.glfrazier.snd.util.VPNFactory;

public class SimVPNFactory implements VPNFactory {

	private EventingSystem eventingSystem;

	static final Map<AddressPair, SimVPN> VPN_MAP = Collections.synchronizedMap(new HashMap<>());

	public SimVPNFactory(EventingSystem es) {
		this.eventingSystem = es;
	}

	@Override
	public VPN createVPN(MessageReceiver local, InetAddress remote, Object keyingMaterial) throws IOException {
		return new SimVPN(local, remote, eventingSystem);
	}


}
