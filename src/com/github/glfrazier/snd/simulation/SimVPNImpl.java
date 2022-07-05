package com.github.glfrazier.snd.simulation;

import static com.github.glfrazier.snd.simulation.SimVPNFactory.VPN_MAP;
import static com.github.glfrazier.snd.util.AddressUtils.addrToString;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventProcessor;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.message.Ack;
import com.github.glfrazier.snd.protocol.message.AcknowledgeMessage;
import com.github.glfrazier.snd.protocol.message.AddIntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionCompletedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.protocol.message.RemoveIntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.SNDMessage;
import com.github.glfrazier.snd.util.AddressUtils.AddressPair;
import com.github.glfrazier.snd.util.TimeAndAcknowledgeMessage;
import com.github.glfrazier.snd.util.VPN;

public class SimVPNImpl implements VPN, EventProcessor {

	private static final long MESSAGE_LATENCY = 10; // 1/100th of a second
	private static final long ACK_TIMEOUT = 6 * MESSAGE_LATENCY;
	private static final Event CHECK_TIMEOUTS = new Event() {
		private final String NAME = "Check Timeouts in SNDP Transmissions.";

		public String toString() {
			return NAME;
		}
	};

	private static enum State {
		UNCONNECTED, OPEN, HALF_CLOSED, CLOSED
	};

	private static final AtomicLong INDEX = new AtomicLong(0);

	private static final boolean RETRANSMITTING = true;

	private final long uuid;
	private final MessageReceiver local;
	private final InetAddress remote;
	private SimVPNImpl remoteVPN;
	private int sequenceNumber;
	private int lastContiguousSequenceNumberReceived;
	private boolean messagesHaveBeenReceived;
	private List<TimeAndAcknowledgeMessage> unacknowledgedMessages;
	private final Set<IntroductionRequest> introductionRequests;
	private final EventingSystem eventingSystem;

	private State state;

	private String stringRep;

	private final Logger LOGGER;
	private boolean pendingTimeoutCheckWakeup;

	public SimVPNImpl(MessageReceiver local, InetAddress remote, EventingSystem eventingSystem) {
		this(local, remote, null, eventingSystem);
	}

	public SimVPNImpl(MessageReceiver local, InetAddress remote, IntroductionRequest introReq,
			EventingSystem eventingSystem) {
		uuid = INDEX.getAndIncrement();
		this.local = local;
		this.remote = remote;
		this.eventingSystem = eventingSystem;
		if (introReq == null) {
			this.introductionRequests = null;
			stringRep = "LongLived{" + addrToString(local.getAddress()) + " <=> " + addrToString(remote) + " (" + uuid
					+ ")}";
		} else {
			this.introductionRequests = Collections.synchronizedSet(new HashSet<>());
			this.introductionRequests.add(introReq);
			stringRep = "Introduced{" + addrToString(local.getAddress()) + " <=> " + addrToString(remote) + " (" + uuid
					+ ")}";
		}

		this.sequenceNumber = 1; // This should be random!!
		this.unacknowledgedMessages = new LinkedList<>();

		this.LOGGER = local.getLogger();
		state = State.UNCONNECTED;
		VPN_MAP.put(new AddressPair(local.getAddress(), remote), this);
		connect();
		if (LOGGER.isLoggable(FINEST)) {
			LOGGER.finest(this + " created.");
		}
	}

	private synchronized void checkTimeouts() {
		if (state == State.CLOSED) {
			// If the VPN is closed, we no longer check for timeouts.
			return;
		}
		if (state == State.OPEN) {
			// We only check for timeouts if the VPN is open
			//
			long now = eventingSystem.getCurrentTime();
			while (true) {
				if (unacknowledgedMessages.isEmpty()) {
					break;
				}
				TimeAndAcknowledgeMessage t = unacknowledgedMessages.get(0);
				if (now - t.time < ACK_TIMEOUT) {
					break;
				}
				// Note that send(Message, boolean) will requeue the message onto the back of
				// the list.
				t = unacknowledgedMessages.remove(0);
				try {
					if (LOGGER.isLoggable(WARNING)) {
						LOGGER.warning(
								this + ": t=" + eventingSystem.getCurrentTime() + ", retransmitting " + t.message);
					}
					send((Message) t.message, RETRANSMITTING);
				} catch (IOException e) {
					// If a retransmission fails, just drop it on the floor. Clearly this VPN is in
					// a bad state.
					LOGGER.severe(this + ": t=" + eventingSystem.getCurrentTime() + ", failed to retransmit "
							+ t.message + ": " + e);
					e.printStackTrace();
				}
			}
		}
		if (unacknowledgedMessages.isEmpty()) {
			pendingTimeoutCheckWakeup = false;
		} else {
			// Only wake up again if there are unacknowledged messages to send
			pendingTimeoutCheckWakeup = true;
			eventingSystem.scheduleEventRelative(this, CHECK_TIMEOUTS, ACK_TIMEOUT);
		}
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
		send(m, false);
	}

	private synchronized void send(Message m, boolean isRetransmission) throws IOException {
		if (LOGGER.isLoggable(FINEST)) {
			LOGGER.finest(this + " sending " + m);
		}
		if (state != State.OPEN) {
			throw new IOException(this + " time=" + eventingSystem.getCurrentTime() + " is not open. state=" + state
					+ ", attempting to send " + m);
		}
		if (!isRetransmission) {
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
			if (m instanceof AcknowledgeMessage) {
				((AcknowledgeMessage) m).setSequenceNumber(sequenceNumber++);
			}
		}
		if (m instanceof Ack) {
			((Ack) m).setLastContiguousSequenceNumberReceived(lastContiguousSequenceNumberReceived);
		}
		if (m instanceof AcknowledgeMessage) {
			if (messagesHaveBeenReceived) {
				((AcknowledgeMessage) m).setLastContiguousSequenceNumberReceived(lastContiguousSequenceNumberReceived);
			}
			unacknowledgedMessages
					.add(new TimeAndAcknowledgeMessage(eventingSystem.getCurrentTime(), (AcknowledgeMessage) m));
			if (!this.pendingTimeoutCheckWakeup) {
				pendingTimeoutCheckWakeup = true;
				eventingSystem.scheduleEventRelative(this, CHECK_TIMEOUTS, ACK_TIMEOUT);
			}
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
			// sanity check!
			if (this.isIntroduced() != remoteVPN.isIntroduced()) {
				throw new IllegalStateException(this + " cannot be connected to " + remoteVPN);
			}
			state = State.OPEN;
		}
		remoteVPN.connect();
	}

	private synchronized void receive(Message m) throws IOException {
		if (LOGGER.isLoggable(FINEST)) {
			LOGGER.finest(this + " received " + m);
		}

		// A closed VPN should never receive a message!
		if (state == State.CLOSED) {
			LOGGER.severe(this + ": receive(" + m + ") invoked on closed VPN.");
			System.exit(-1);
		}

		if (m instanceof Ack) {
			Ack ack = (Ack) m;
			int lcsnr = ack.getLastContiguousSequenceNumberReceived();
			processLCSNR(lcsnr);
			return;
		}

		if (m instanceof AcknowledgeMessage) {
			AcknowledgeMessage ackMsg = (AcknowledgeMessage) m;
			// First, each SND message received is acknowledging messages that this node
			// sent. Remove those acknowledged messages from the list of unacknowledged
			// messages. Every message in the unacknowledged list whose sequence number is
			// less than or equal to the lastContiguousSequenceNumber is acked.
			int lcsnr = ackMsg.getLastContiguousSequenceNumberReceived();
			processLCSNR(lcsnr);

			// Second, we have to acknowledge the SNDMessages we have received. So, we track
			// their sequence numbers.
			if (!messagesHaveBeenReceived) {
				// This is the first message received on this VPN.
				// Initialize lastContiguousSequenceNumberReceived to this message's sequence
				// number.
				messagesHaveBeenReceived = true;
				lastContiguousSequenceNumberReceived = ackMsg.getSequenceNumber();
			} else {
				int sn = ackMsg.getSequenceNumber();
				if (sn <= lastContiguousSequenceNumberReceived && //
				// logic to handle roll-over
						(sn > 0 || lastContiguousSequenceNumberReceived < 0)) {
					// This is a retransmission of a message we already successfully received.
					// Ignore it!
					LOGGER.warning(this + ": ignoring " + m + " because its sequence number is too low.");
					return;
				}
				if (sn == lastContiguousSequenceNumberReceived + 1) {
					lastContiguousSequenceNumberReceived = sn;
				} else {
					// We dropped a message somewhere!!
					// So ignore this message.
					LOGGER.warning(this + ": ignoring " + m + " because we skipped a sequence number.");
					return;
				}
			}
		}

		// Another implementation-specific message, but one that should not arrive at
		// closed VPNs.
		if (m instanceof RemoveIntroductionRequestMessage) {
			try {
				send(new Ack(remote, local.getAddress()));
			} catch (IOException e1) {
				// Ignore a failed ack.
				e1.printStackTrace();
			}
			this.removeIntroductionRequest(((RemoveIntroductionRequestMessage) m).getIntroductionRequest());
			if (remoteVPN.LOGGER.isLoggable(FINEST)) {
				remoteVPN.LOGGER
						.finest(this + " received " + m + ", now has introductionRequests=" + introductionRequests);
			}
			return;
		}

		// Another implementation-specific message, but one that should not arrive at
		// closed VPNs.
		if (m instanceof AddIntroductionRequestMessage) {
			this.addIntroductionRequest(((AddIntroductionRequestMessage) m).getIntroductionRequest());
			try {
				send(new Ack(remote, local.getAddress()));
			} catch (IOException e1) {
				// Ignore a failed ack.
				e1.printStackTrace();
			}
			if (remoteVPN.LOGGER.isLoggable(FINEST)) {
				remoteVPN.LOGGER
						.finest(this + " received " + m + ", now has introductionRequests=" + introductionRequests);
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

	/**
	 * Process the neighbor's last contiguous sequence number received (LCSNR). This
	 * method is ONLY called from receive(Message), and so the VPN's mutex is
	 * grabbed.
	 * 
	 * @param lcsnr the last contiguous sequence number received by the neighbor
	 *              acknowledging receptions.
	 */
	private void processLCSNR(int lcsnr) {
		for (Iterator<TimeAndAcknowledgeMessage> iter = unacknowledgedMessages.iterator(); iter.hasNext();) {
			AcknowledgeMessage unacked = iter.next().message;
			if (unacked.getSequenceNumber() <= lcsnr) {
				iter.remove();
			}
		}
		if (state == State.HALF_CLOSED && unacknowledgedMessages.isEmpty()) {
			state = State.CLOSED;
		}
	}

	public InetAddress getRemote() {
		return remote;
	}

	public synchronized void close() {
		if (state == State.CLOSED || state == State.HALF_CLOSED) {
			return;
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
		if (e == CHECK_TIMEOUTS) {
			checkTimeouts();
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

	/**
	 * 
	 * @param request
	 * @return true if the introduction request was new and successfully added
	 * @throws IOException
	 */
	public synchronized boolean addIntroductionRequest(IntroductionRequest request) throws IOException {
		if (state == State.CLOSED) {
			throw new IOException(this + " time=" + eventingSystem.getCurrentTime()
					+ " Cannot add an introduction request to a closed VPN. state = " + state);
		}
		if (introductionRequests == null) {
			return false;
		}
		boolean result = introductionRequests.add(request);
		if (result) {
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
			VPN_MAP.put(new AddressPair(local.getAddress(), remote), this);
		}
		return result;
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
			state = State.HALF_CLOSED;
			if (LOGGER.isLoggable(FINEST)) {
				LOGGER.finest(this + " is now half closed!!!!");
			}
			result = true;
		}
		return result;
	}

	public synchronized boolean hasNoIntroductions() {
		if (introductionRequests == null) {
			throw new UnsupportedOperationException(
					this + " is not an introduced VPN, so hasNoIntroductions() is nonsense.");
		}
		return introductionRequests.isEmpty();
	}

}
