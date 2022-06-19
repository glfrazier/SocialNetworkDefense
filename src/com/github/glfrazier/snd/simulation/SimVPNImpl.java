package com.github.glfrazier.snd.simulation;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

	private final long uuid;
	private final MessageReceiver local;
	private final InetAddress remote;
	private SimVPNImpl remoteVPN;
	private final Set<IntroductionRequest> introductionRequests;
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
		this.introductionRequests = null;
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
		connect(null);
	}

	public SimVPNImpl(MessageReceiver local, InetAddress remote, IntroductionRequest introReq,
			EventingSystem eventingSystem, Map<InetAddress, Map<IntroductionRequest, SimVPNImpl>> ephemeralVPNMap) {
		uuid = INDEX.getAndIncrement();
		this.local = local;
		this.remote = remote;
		this.eventingSystem = eventingSystem;
		this.introductionRequests = new HashSet<>();
		this.introductionRequests.add(introReq);
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
		connect(introReq);
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

	public synchronized void send(Message m) throws IOException {
		if (closed) {
			throw new IOException(this + " is closed.");
		}
		if (!connected) {
			throw new IOException(this + " is not connected!");
		}
		eventingSystem.scheduleEventRelative(remoteVPN, m, MESSAGE_LATENCY);
	}

	/**
	 * Given that two Communicators A and B are linked via VPN, this method
	 * associates A's VPN to B with B's VPN to A.
	 */
	private void connect(IntroductionRequest ir) {
		if (connected) {
			return;
		}
		if (introductionRequests == null) {
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
					remoteVPN = remoteVPNMap.get(ir);
					if (remoteVPN == null) {
						return;
					}
					// else
					connected = true;
				}
			}
		}
		remoteVPN.connect(ir);
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
		eventingSystem.scheduleEventRelative(this, new CloseVpnEvent(null), MESSAGE_LATENCY + 1);
	}

	public void close(IntroductionRequest request) {
		if (closed) {
			return;
		}
		eventingSystem.scheduleEventRelative(this, new CloseVpnEvent(request), MESSAGE_LATENCY + 1);
	}

	private synchronized void privateClose(IntroductionRequest introductionRequest, boolean first) {
		if (closed) {
			return;
		}
		if (introductionRequests == null) {
			synchronized (simulationVPNMap) {
				Map<InetAddress, SimVPNImpl> vpnMap = simulationVPNMap.get(local.getAddress());
				vpnMap.remove(remote);
			}
		} else {
			if (introductionRequest == null) {
				for (IntroductionRequest ir : introductionRequests) {
					introductionRequests.remove(ir);
					synchronized (ephemeralVPNMap) {
						Map<IntroductionRequest, SimVPNImpl> vpnMap = ephemeralVPNMap.get(local.getAddress());
						vpnMap.remove(ir);
					}
				}
			} else {
				introductionRequests.remove(introductionRequest);
				synchronized (ephemeralVPNMap) {
					Map<IntroductionRequest, SimVPNImpl> vpnMap = ephemeralVPNMap.get(local.getAddress());
					vpnMap.remove(introductionRequest);
				}
			}
		}
		closed = true;
		local.vpnClosed(this);
		if (first && remoteVPN != null) {
			remoteVPN.privateClose(introductionRequest, false);
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
		if (e instanceof CloseVpnEvent) {
			IntroductionRequest ir = ((CloseVpnEvent) e).request;
			if (remoteVPN == null) {
				privateClose(ir, true);
			} else {
				if (AddressUtils.compare(local.getAddress(), remote) < 0) {
					remoteVPN.privateClose(ir, true);
				} else {
					privateClose(ir, true);
				}
			}
			return;
		}
	}

	@Override
	public String toString() {
		return stringRep;
	}

	public synchronized IntroductionRequest getIntroductionRequest(InetAddress requester, InetAddress destination) {
		if (introductionRequests == null || introductionRequests.isEmpty()) {
			return null;
		}
		for (IntroductionRequest ir : introductionRequests) {
			if (ir.requester.equals(requester) && ir.destination.equals(destination)) {
				return ir;
			}
		}
		return null;
	}

	public synchronized boolean addIntroductionRequest(IntroductionRequest request) {
		if (closed) {
			return false;
		}
		introductionRequests.add(request);
		Map<IntroductionRequest, SimVPNImpl> map = null;
		synchronized (ephemeralVPNMap) {
			map = ephemeralVPNMap.get(local.getAddress());
		}
		map.put(request, this);
		return true;
	}

	private static class CloseVpnEvent implements Event {
		public final IntroductionRequest request;

		public CloseVpnEvent(IntroductionRequest ir) {
			this.request = ir;
		}
	}

}
