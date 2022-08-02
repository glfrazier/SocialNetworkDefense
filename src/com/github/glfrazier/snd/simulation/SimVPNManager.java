package com.github.glfrazier.snd.simulation;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.util.AddressUtils.AddressPair;
import com.github.glfrazier.snd.util.VPNManager;

public class SimVPNManager implements VPNManager {

	private EventingSystem eventingSystem;

	private MessageReceiver local;

	static final Map<AddressPair, SimVPN> VPN_MAP = Collections.synchronizedMap(new HashMap<>());

	public SimVPNManager(EventingSystem es, MessageReceiver local) {
		this.eventingSystem = es;
		this.local = local;
	}

	@Override
	public synchronized void createVPN(InetAddress remote, Object keyingMaterial) throws IOException {
		SimVPN vpn = new SimVPN(local, remote, eventingSystem);
	}


	@Override
	public synchronized void closeVPN(InetAddress remote) {
		SimVPN vpn = VPN_MAP.get(new AddressPair(local.getAddress(), remote));
		if (vpn == null) return;
		vpn.close();
	}


}
