package com.github.glfrazier.snd.protocol;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.node.SNDNode;
import com.github.glfrazier.snd.protocol.message.AckMessage;
import com.github.glfrazier.snd.protocol.message.SNDPMessage;
import com.github.glfrazier.statemachine.State;
import com.github.glfrazier.statemachine.State.Action;
import com.github.glfrazier.statemachine.StateMachine;
import com.github.glfrazier.statemachine.Transition;

/**
 * The finite state machine for sending a message. Every SNDP message is
 * acknowledged. If an AckMessage is not received within ACK_TIMEOUT
 * milliseconds, the SNDP message is retransmitted; a message can be transmitted
 * TRANSMISSON_ATTEMPT times before a failure is declared.
 * 
 * This state machine (SM) has three states:
 * <dl>
 * <dt>transmissionState</dt>
 * <dd>The initial state. The message is sent, and a timeout is sent. If the
 * send() results in an IOException, the SM transitions immediately to
 * failureState. An AckMessage transitions the SM to successState. The
 * TRANSMISSION_ATTEMPT th timeout transitions the SM to failureState. Prior
 * timeouts transition the SM back to the transmissionState.</dd>
 * <dt>successState</dt>
 * <dd>A terminal state. The transmission was acknowledged.</dd>
 * <dt>failureState</dt>
 * <dd>A terminal state. The transmission was not acknowledged.</dd>
 * </dl>
 * 
 */
public class SNDPMessageTransmissionProtocol extends StateMachine {

	private SNDNode node;
	private SNDPMessage message;
	private int attempts = 0;

	private StateMachine superProtocol;

	protected static final int MAX_ATTEMPTS = 3;
	public static final int TRANSMISSION_LATENCY = 10;
	private static final long ACK_TIMEOUT = 3 * TRANSMISSION_LATENCY;

	public SNDPMessageTransmissionProtocol(SNDNode node, StateMachine protocol, SNDPMessage message, boolean verbose) {
		super("SMDP MTP(" + message + ")", EventEqualityMode.CLASS_EQUALS, node.getEventingSystem());
		this.node = node;
		this.superProtocol = protocol;
		this.message = message;
		this.verbose = verbose;

		this.addTransition(new Transition(transmissionState, getTimeoutEvent().getClass(), transmissionState));
		this.addTransition(new Transition(transmissionState, AckMessage.class, successState));
		this.addTransition(new Transition(transmissionState, FAILURE_EVENT.getClass(), failureState));

		this.setStartState(transmissionState);
	}

	private static Action failureAction = new State.Action() {
		@Override
		public void act(StateMachine sm, State s, Event e) {
			SNDPMessageTransmissionProtocol stp = (SNDPMessageTransmissionProtocol) sm;
			stp.node.unregisterAckWaiter(stp.message.getIdentifier());
			if (stp.superProtocol != null) {
				stp.superProtocol.receive(FAILURE_EVENT);
			}
		}
	};

	private static Action successAction = new State.Action() {
		@Override
		public void act(StateMachine sm, State s, Event e) {
			SNDPMessageTransmissionProtocol stp = (SNDPMessageTransmissionProtocol) sm;
			if (stp.superProtocol != null) {
				stp.superProtocol.receive(SUCCESS_EVENT);
			}
		}
	};

	private static Action transmissionAction = new State.Action() {
		@Override
		public void act(StateMachine sm, State s, Event e) {
			SNDPMessageTransmissionProtocol stp = (SNDPMessageTransmissionProtocol) sm;
			if (stp.attempts >= MAX_ATTEMPTS) {
				stp.receive(FAILURE_EVENT);
			}
			stp.attempts++;
			stp.node.send(stp, stp.message);
			stp.scheduleTimeout(ACK_TIMEOUT);
		}
	};

	private static State transmissionState = new State("transmission state", transmissionAction);
	private static State successState = new State("success state"); // , successAction);
	private static State failureState = new State("failure state", failureAction);

}
