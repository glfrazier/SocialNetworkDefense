package com.github.glfrazier.snd.protocol;

import static com.github.glfrazier.snd.node.Node.TRANSMISSION_LATENCY;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.node.Node;
import com.github.glfrazier.snd.protocol.message.AckMessage;
import com.github.glfrazier.snd.protocol.message.AddIntroductionRequestMessage;
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

	private Node node;
	private SNDPMessage message;
	private int attempts = 0;

	private StateMachine superProtocol;

	protected static final int MAX_ATTEMPTS = 3;
	private static final long ACK_TIMEOUT = 3 * TRANSMISSION_LATENCY;

	public SNDPMessageTransmissionProtocol(Node node, StateMachine protocol, SNDPMessage message, boolean verbose) {
		// TODO this is an expensive constructor, as it is calling the toString() method
		// on the message. Consider changing this. Or giving message a method whose
		// expense can vary based on whether we are running verbosely or not.
		super("SMDP MTP(" + message + ")", EventEqualityMode.CLASS_EQUALS, node.getEventingSystem());
		this.node = node;
		this.superProtocol = protocol;
		this.message = message;
		this.verbose = verbose;

		// GLF DEBUG
		if ((message instanceof AddIntroductionRequestMessage) && (superProtocol != null)) {
			new Exception("============================\nAddIntroductionRequest!\n++++++++++++++++++++")
					.printStackTrace();
			System.exit(-1);
		}
		this.addTransition(new Transition(transmissionState, getTimeoutEvent().getClass(), transmissionState));
		this.addTransition(new Transition(transmissionState, AckMessage.class, successState));
		this.addTransition(new Transition(transmissionState, FAILURE_EVENT.getClass(), failureState));

		this.setStartState(transmissionState);
	}

	public boolean succeeded() {
		return this.getCurrentState() == successState;
	}

	private static Action failureAction = new State.Action() {
		@Override
		public void act(StateMachine sm, State s, Event e) {
			SNDPMessageTransmissionProtocol stp = (SNDPMessageTransmissionProtocol) sm;
			stp.node.unregisterAckWaiter(stp.message.getIdentifier());
			if (stp.verbose) {
				System.out.println(stp.node.addTimePrefix(stp + ": closing link to " + stp.message.getDst()));
			}
			stp.node.removeAllIntroductionRequestsFromVPN(stp.message.getDst());
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
				return;
			}
			stp.attempts++;
			if (stp.verbose) {
				System.out.println(
						stp.node.addTimePrefix(stp + ": attempt " + stp.attempts + " at sending " + stp.message));
			}
			stp.node.send(stp, stp.message);
			stp.scheduleTimeout(ACK_TIMEOUT);
		}
	};

	private static State transmissionState = new State("transmission state", transmissionAction);
	private static State successState = new State("success state", successAction);
	private static State failureState = new State("failure state", failureAction);

}
