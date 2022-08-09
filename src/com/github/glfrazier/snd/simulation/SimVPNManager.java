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
		if (vpn == null)
			return;
		// A node will close a VPN at the same time that an ACK is being sent. So, we
		// delay the actual closing of the VPN by one time unit. This does not prevent a
		// host from creating a new VPN to the same neighbor in the meantime.
		eventingSystem.scheduleEventRelative(vpn, SimVPN.LOCAL_CLOSE_VPN_EVENT, 1);
	}

}
