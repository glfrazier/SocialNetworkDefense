package com.github.glfrazier.snd.simulation;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventProcessor;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.util.VPN;
import com.github.glfrazier.snd.util.VPNEndpoint;

public class SimVPNImpl implements VPN, EventProcessor {

	public static final long MESSAGE_LATENCY = 10; // 1/100th of a second

	private static final AtomicLong INDEX = new AtomicLong(0);

	private final long uuid;
	private final VPNEndpoint local;
	private final InetAddress remote;
	private SimVPNImpl remoteVPN;
	private final IntroductionRequest introductionRequest;
	private final EventingSystem eventingSystem;
	private boolean closed = false;
	private final Map<InetAddress, Map<InetAddress, SimVPNImpl>> simulationVPNMap;
	private final Map<InetAddress, Map<IntroductionRequest, SimVPNImpl>> ephemeralVPNMap;

	private boolean connected;

	public SimVPNImpl(VPNEndpoint local, InetAddress remote, EventingSystem es,
			Map<InetAddress, Map<InetAddress, SimVPNImpl>> simulationVPNMap) {
		uuid = INDEX.getAndIncrement();
		this.local = local;
		this.remote = remote;
		this.eventingSystem = es;
		this.introductionRequest = null;
		this.simulationVPNMap = simulationVPNMap;
		this.ephemeralVPNMap = null;
		Map<InetAddress, SimVPNImpl> vpnMap = null;
		synchronized (simulationVPNMap) {
			vpnMap = simulationVPNMap.get(local.getAddress());
			if (vpnMap == null) {
				vpnMap = new HashMap<>();
				simulationVPNMap.put(local.getAddress(), vpnMap);
			}
			synchronized (vpnMap) {
				vpnMap.put(remote, this);
			}
		}
		connect();
	}

	public SimVPNImpl(VPNEndpoint local, InetAddress remote, IntroductionRequest introReq, EventingSystem eventingSystem,
			Map<InetAddress, Map<IntroductionRequest, SimVPNImpl>> ephemeralVPNMap) {
		uuid = INDEX.getAndIncrement();
		this.local = local;
		this.remote = remote;
		this.eventingSystem = eventingSystem;
		this.introductionRequest = introReq;
		this.ephemeralVPNMap = ephemeralVPNMap;
		this.simulationVPNMap = null;
		Map<IntroductionRequest, SimVPNImpl> vpnMap = null;
		synchronized (ephemeralVPNMap) {
			vpnMap = ephemeralVPNMap.get(local.getAddress());
			if (vpnMap == null) {
				vpnMap = new HashMap<>();
				ephemeralVPNMap.put(local.getAddress(), vpnMap);
			}
			synchronized (vpnMap) {
				vpnMap.put(introReq, this);
			}
		}
		connect();
	}

	public int hashCode() {
		return Long.hashCode(uuid);
	}

	public boolean equals(Object o) {
		if (!(o instanceof SimVPNImpl)) {
			return false;
		}
		return uuid == ((SimVPNImpl) o).uuid;
	}

	@Override
	public void send(Message m) throws IOException {
		if (!connected) {
			connect();
		}
		if (!connected) {
			throw new IOException();
		}
		eventingSystem.scheduleEventRelative(remoteVPN, m, MESSAGE_LATENCY);
	}

	/**
	 * Given that two Communicators A and B are linked via VPN, this method
	 * associates A's VPN to B with B's VPN to A.
	 */
	private void connect() {
		if (introductionRequest == null) {
			synchronized (simulationVPNMap) {
				Map<InetAddress, SimVPNImpl> remoteVPNMap = simulationVPNMap.get(remote);
				if (remoteVPNMap == null) {
					return;
				}
				synchronized (remoteVPNMap) {
					remoteVPN = remoteVPNMap.get(local.getAddress());
					if (remoteVPN == null) {
						return;
					}
					// else
					connected = true;
				}
			}
		} else {
			synchronized (ephemeralVPNMap) {
				Map<IntroductionRequest, SimVPNImpl> remoteVPNMap = ephemeralVPNMap.get(remote);
				if (remoteVPNMap == null) {
					return;
				}
				synchronized (remoteVPNMap) {
					remoteVPN = remoteVPNMap.get(introductionRequest);
					if (remoteVPN == null) {
						return;
					}
					// else
					connected = true;
				}
			}
		}
	}

	private synchronized void receive(Message m) throws IOException {
		if (closed) {
			throw new IOException("receive(Message) invoked on closed VPN.");
		}
		local.receive(m, this);
	}

	@Override
	public InetAddress getRemote() {
		return remote;
	}

	@Override
	public synchronized void close() {
		if (closed) {
			return;
		}
		synchronized (simulationVPNMap) {
			Map<InetAddress, SimVPNImpl> vpnMap = simulationVPNMap.get(local.getAddress());
			vpnMap.remove(remote);
		}
		closed = true;
	}

	@Override
	public void process(Event e, EventingSystem eventingSystem) {
		if (e instanceof Message) {
			try {
				receive((Message) e);
			} catch (IOException e1) {
				eventingSystem.scheduleEventRelative(remoteVPN, e, 0);
			}
		}
	}
	
	@Override
	public String toString() {
		return "simvpn " + local + "<==>" + remote;
	}

}
