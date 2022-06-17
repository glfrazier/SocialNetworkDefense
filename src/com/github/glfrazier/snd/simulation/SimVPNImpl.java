package com.github.glfrazier.snd.simulation;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventProcessor;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.util.AddressUtils;
import com.github.glfrazier.snd.util.VPN;

public class SimVPNImpl implements VPN, EventProcessor {

	public static final long MESSAGE_LATENCY = 10; // 1/100th of a second

	private static final AtomicLong INDEX = new AtomicLong(0);

	private static final Event CLOSE_VPN_EVENT = new Event() {
		private final String name = "Close VPN Event";

		public String toString() {
			return name;
		}
	};

	private final long uuid;
	private final MessageReceiver local;
	private final InetAddress remote;
	private SimVPNImpl remoteVPN;
	private final IntroductionRequest introductionRequest;
	private final EventingSystem eventingSystem;
	private boolean closed = false;
	private final Map<InetAddress, Map<InetAddress, SimVPNImpl>> simulationVPNMap;
	private final Map<InetAddress, Map<IntroductionRequest, SimVPNImpl>> ephemeralVPNMap;

	private boolean connected;

	private String stringRep;

	public SimVPNImpl(MessageReceiver local, InetAddress remote, EventingSystem es,
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
		stringRep = "LongLived{" + local.getAddress() + "<==>" + remote + "}";
		connect();
	}

	public SimVPNImpl(MessageReceiver local, InetAddress remote, IntroductionRequest introReq,
			EventingSystem eventingSystem, Map<InetAddress, Map<IntroductionRequest, SimVPNImpl>> ephemeralVPNMap) {
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
		stringRep = "Introduced{" + local.getAddress() + "<==>" + remote + "}";
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
		local.receive(m);
	}

	public InetAddress getRemote() {
		return remote;
	}

	public void close() {
		if (closed) {
			return;
		}
		// Schedule this VPN to be closed after a recently-transmitted message might be
		// received
		eventingSystem.scheduleEventRelative(this, CLOSE_VPN_EVENT, MESSAGE_LATENCY + 1);
	}

	private synchronized void privateClose(boolean first) {
		if (closed) {
			return;
		}
		if (introductionRequest == null) {
			synchronized (simulationVPNMap) {
				Map<InetAddress, SimVPNImpl> vpnMap = simulationVPNMap.get(local.getAddress());
				vpnMap.remove(remote);
			}
		} else {
			synchronized (ephemeralVPNMap) {
				Map<IntroductionRequest, SimVPNImpl> vpnMap = ephemeralVPNMap.get(local.getAddress());
				vpnMap.remove(introductionRequest);
			}
		}
		closed = true;
		local.vpnClosed(this);
		if (first && remoteVPN != null) {
			remoteVPN.privateClose(false);
		}
		remoteVPN = null;
	}

	@Override
	public void process(Event e, EventingSystem eventingSystem) {
		if (e instanceof Message) {
			try {
				receive((Message) e);
			} catch (IOException e1) {
				e1.printStackTrace();
				System.exit(0);
			}
			return;
		}
		if (e == CLOSE_VPN_EVENT) {
			if (remoteVPN == null) {
				privateClose(true);
			} else {
				if (AddressUtils.compare(local.getAddress(), remote) < 0) {
					remoteVPN.privateClose(true);
				} else {
					privateClose(true);
				}
			}
			return;
		}
	}

	@Override
	public String toString() {
		return stringRep;
	}

	public IntroductionRequest getIntroductionRequest() {
		return introductionRequest;
	}

}
