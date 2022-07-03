package com.github.glfrazier.snd.simulation;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;
import static com.github.glfrazier.snd.simulation.SimVPNFactory.VPN_MAP;
import static java.util.logging.Level.FINEST;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventProcessor;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.message.IntroductionCompletedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.protocol.message.SNDMessage;
import com.github.glfrazier.snd.util.AddressUtils.AddressPair;
import com.github.glfrazier.snd.util.VPN;

public class SimVPNImpl implements VPN, EventProcessor {

	public static final long MESSAGE_LATENCY = 10; // 1/100th of a second

	private static enum State {
		UNCONNECTED, OPEN, HALF_CLOSED, CLOSED
	};

	private static final AtomicLong INDEX = new AtomicLong(0);

	private static final Message CLOSE_MSG = new Message(null, null) {
		private static final long serialVersionUID = 1L;
		private String NAME = "CLOSE MSG";

		public String toString() {
			return NAME;
		}
	};

	private static final Message CLOSE_ACK = new Message(null, null) {
		private static final long serialVersionUID = 1L;
		private String NAME = "CLOSE ACK MSG";

		public String toString() {
			return NAME;
		}
	};

	private final long uuid;
	private final MessageReceiver local;
	private final InetAddress remote;
	private SimVPNImpl remoteVPN;
	private final Set<IntroductionRequest> introductionRequests;
	private final EventingSystem eventingSystem;

	private State state;

	private String stringRep;

	private final Logger LOGGER;

	public SimVPNImpl(MessageReceiver local, InetAddress remote, EventingSystem es) {
		uuid = INDEX.getAndIncrement();
		this.local = local;
		this.remote = remote;
		this.eventingSystem = es;
		this.introductionRequests = null;
		this.LOGGER = local.getLogger();
		state = State.UNCONNECTED;
		VPN_MAP.put(new AddressPair(local.getAddress(), remote), this);
		stringRep = "LongLived{" + addrToString(local.getAddress()) + " <=> " + addrToString(remote) + " (" + uuid
				+ ")}";
		if (LOGGER.isLoggable(FINEST)) {
			LOGGER.finest(this + " created.");
		}
		connect();
	}

	public SimVPNImpl(MessageReceiver local, InetAddress remote, IntroductionRequest introReq,
			EventingSystem eventingSystem) {
		uuid = INDEX.getAndIncrement();
		this.local = local;
		this.remote = remote;
		this.eventingSystem = eventingSystem;
		this.introductionRequests = Collections.synchronizedSet(new HashSet<>());
		this.introductionRequests.add(introReq);
		this.LOGGER = local.getLogger();
		state = State.UNCONNECTED;
		VPN_MAP.put(new AddressPair(local.getAddress(), remote), this);
		stringRep = "Introduced{" + addrToString(local.getAddress()) + " <=> " + addrToString(remote) + " (" + uuid
				+ ")}";
		if (LOGGER.isLoggable(FINEST)) {
			LOGGER.finest(this + " created, introductionRequests=" + this.introductionRequests);
		}
		connect();
	}

	public boolean isIntroduced() {
		return introductionRequests != null;
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
		if (LOGGER.isLoggable(FINEST)) {
			LOGGER.finest(this + " sending " + m);
		}
		if (state != State.OPEN) {
			throw new IOException(this + " time=" + eventingSystem.getCurrentTime() + " is not open. state=" + state + ", attempting to send " + m);
		}
		if (m instanceof IntroductionRequestMessage) {
			IntroductionRequest ir = ((IntroductionRequestMessage) m).getIntroductionRequest();
			this.addIntroductionRequest(ir);
		}
		if (m instanceof IntroductionDeniedMessage || //
				m instanceof IntroductionCompletedMessage) {
			IntroductionRequest ir = ((SNDMessage) m).getIntroductionRequest();
			if (remoteVPN.LOGGER.isLoggable(FINEST)) {
				remoteVPN.LOGGER.finest(this + " sending " + m + ", removing " + ir);
			}
			this.removeIntroductionRequest(ir);
		}
		eventingSystem.scheduleEventRelative(remoteVPN, m, MESSAGE_LATENCY);
	}

	/**
	 * Given that two Communicators A and B are linked via VPN, this method
	 * associates A's VPN to B with B's VPN to A.
	 */
	private void connect() {
		synchronized (this) {
			if (state == State.OPEN) {
				return;
			}
			remoteVPN = VPN_MAP.get(new AddressPair(remote, local.getAddress()));
			if (remoteVPN == null) {
				return;
			}
			state = State.OPEN;
		}
		remoteVPN.connect();
	}

	private synchronized void receive(Message m) throws IOException {
		if (LOGGER.isLoggable(FINEST)) {
			LOGGER.finest(this + " received " + m);
		}

		// Messages that are processed within the VPN, and so are not received by the
		// owning node.
		// These are specific to this VPN implementation.
		if (m.equals(CLOSE_MSG)) {
			VPN_MAP.remove(new AddressPair(local.getAddress(), remote));
			send(CLOSE_ACK);
			state = State.CLOSED;
			local.vpnClosed(this);
			return;
		}
		if (m.equals(CLOSE_ACK)) {
			if (introductionRequests != null && !introductionRequests.isEmpty()) {
				if (remoteVPN.LOGGER.isLoggable(FINEST)) {
					remoteVPN.LOGGER.finest(this + " is erroneously ignornig a CLOSE_ACK message. introductionRequests="
							+ introductionRequests);
				}
				return;
			}
			VPN_MAP.remove(new AddressPair(local.getAddress(), remote));
			state = State.CLOSED;
			local.vpnClosed(this);
			return;
		}

		// A closed VPN should never receive a message!
		if (state == State.CLOSED) {
			throw new IllegalStateException(this + ": receive(" + m + ") invoked on closed VPN.");
		}

		// Another implementation-specific message, but one that should not arrive at
		// closed VPNs.
		if (m instanceof RemoveIntroductionRequestMessage) {
			this.removeIntroductionRequest(((RemoveIntroductionRequestMessage) m).request);
			if (remoteVPN.LOGGER.isLoggable(FINEST)) {
				remoteVPN.LOGGER.finest(this + " received " + m
						+ ", now has introductionRequests=" + introductionRequests);
			}
			return;
		}

		// SND Messages that all VPN implementations are required to process. Note that
		// these are ALSO received by the owning node.
		if (m instanceof IntroductionRequestMessage) {
			IntroductionRequest ir = ((IntroductionRequestMessage) m).getIntroductionRequest();
			this.addIntroductionRequest(ir);
		}
		if (m instanceof IntroductionDeniedMessage || //
				m instanceof IntroductionCompletedMessage) {
			IntroductionRequest ir = ((SNDMessage) m).getIntroductionRequest();
			this.removeIntroductionRequest(ir);
		}

		// The owning node receives the message.
		local.receive(m);
	}

	public InetAddress getRemote() {
		return remote;
	}

	public synchronized void close() {
		if (state == State.CLOSED || state == State.HALF_CLOSED) {
			return;
		}
		try {
			send(CLOSE_MSG);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		VPN_MAP.remove(new AddressPair(local.getAddress(), remote));
		if (LOGGER.isLoggable(FINEST)) {
			LOGGER.finest(this + " close invoked. Going to HALF_CLOSED.");
		}
		state = State.HALF_CLOSED;
	}

	public synchronized boolean close(IntroductionRequest request) {
		LOGGER.finest(this + " close(" + request + ") invoked.");
		if (state != State.OPEN) {
			return false;
		}
		if (!isIntroduced()) {
			throw new UnsupportedOperationException(this + ": Invoked close(IntroductionRequest) on a long-lived VPN!");
		}
		if (!introductionRequests.contains(request)) {
			return false;
		}
		try {
			send(new RemoveIntroductionRequestMessage(remote, local.getAddress(), request));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return this.removeIntroductionRequest(request);
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
	}

	@Override
	public String toString() {
		return stringRep + "(" + state + ")";
	}

	public synchronized IntroductionRequest getIntroductionRequest(InetAddress destination) {
		if (introductionRequests == null || introductionRequests.isEmpty()) {
			return null;
		}
		for (IntroductionRequest ir : introductionRequests) {
			if (ir.destination.equals(destination)) {
				return ir;
			}
		}
		return null;
	}

	public synchronized void addIntroductionRequest(IntroductionRequest request) throws IOException {
		if (state == State.CLOSED) {
			throw new IOException(this + " time=" + eventingSystem.getCurrentTime() + " Cannot add an introduction request to a closed VPN. state = " + state);
		}
		if (introductionRequests == null) {
			// introductionRequests is null when the link is not introduced. So, ignore
			// this.
			return;
		}
		if (introductionRequests.add(request)) {
			if (LOGGER.isLoggable(FINEST)) {
				LOGGER.finest(this + " added " + request);
			}
		} else {
			if (LOGGER.isLoggable(FINEST)) {
				LOGGER.finest(this + " tried to add " + request + ", but it was already there.");
			}
		}
		if (state == State.HALF_CLOSED) {
			if (LOGGER.isLoggable(FINEST)) {
				LOGGER.finest(this + " took state from HALF_CLOSED to OPEN.");
			}
			state = State.OPEN;
		}
	}

	private synchronized boolean removeIntroductionRequest(IntroductionRequest request) {
		if (state == State.CLOSED) {
			return false;
		}
		if (introductionRequests == null) {
			// introductionRequests is null when the link is not introduced. So, ignore
			// this.
			return false;
		}
		introductionRequests.remove(request);
		if (LOGGER.isLoggable(FINEST)) {
			LOGGER.finest(this + " removed " + request);
		}
		boolean result = false;
		if (introductionRequests.isEmpty()) {
			if (LOGGER.isLoggable(FINEST)) {
				LOGGER.finest(this + " has no introduction requests -- go to half-closed.");
			}
			VPN_MAP.remove(new AddressPair(local.getAddress(), remote));
			if (LOGGER.isLoggable(FINEST)) {
				LOGGER.finest(this + " removed all introduction requests and is closing.");
			}
			try {
				send(CLOSE_ACK);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			state = State.HALF_CLOSED;
			if (LOGGER.isLoggable(FINEST)) {
				LOGGER.finest(this + " is now half closed!!!!");
			}
			result = true;
		}
		return result;
	}

	private static class RemoveIntroductionRequestMessage extends Message {

		private static final long serialVersionUID = 1L;
		public final IntroductionRequest request;

		public RemoveIntroductionRequestMessage(InetAddress dst, InetAddress src, IntroductionRequest req) {
			super(dst, src);
			request = req;
		}
		
		public String toString() {
			return super.toString("RemoveIntroductionRequest") + "(" + request + ")";
		}

	}

	public synchronized boolean hasNoIntroductions() {
		if (introductionRequests == null) {
			throw new UnsupportedOperationException(
					this + " is not an introduced VPN, so hasNoIntroductions() is nonsense.");
		}
		return introductionRequests.isEmpty();
	}

}
