package com.github.glfrazier.snd.protocol;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.node.Node;
import com.github.glfrazier.snd.protocol.message.AddIntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionAcceptedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionCompletedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionOfferMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRefusedMessage;
import com.github.glfrazier.statemachine.State;
import com.github.glfrazier.statemachine.State.Action;
import com.github.glfrazier.statemachine.StateMachine;
import com.github.glfrazier.statemachine.Transition;

/**
 * The protocol state machine:
 * <dl>
 * <dt>decideToAcceptState</dt>
 * <dd>Decide whether to accept the introductionRequest and see if the VPN
 * already exists. If we are not going to accept the introductionRequest, go to
 * refuseState. If the VPN does not exist, go to createVPNState. If the VPN
 * exists, send the AddIntroductionRequestMessage and wait for success or
 * failure. Success transitions to the sendIntroductionCompleted state. Failure
 * transitions to the createVPNState.</dd>
 * <dt>createVPNState</dt>
 * <dd>Create the VPN. If this fails (!?), transition to the sendRefuseState.
 * Otherwise, send the IntroductionAcceptedMessage. If this fails, transition to
 * the removeVPNState. If this succeeds, transition to the terminalState.</dd>
 * <dt>removeVPNState</dt>
 * <dd>Remove the introductionRequest from the VPN. Transition to the
 * terminalState.</dd>
 * <dt>sendIntroductionCompletedState</dt>
 * <dd>Send the IntroductionCompletedMessage to the introducer, signaling that
 * the VPN already exists. Transition to the terminalState.
 * <dt>sendRefuseState</dt>
 * <dd>Send an IntroductionRefusedMessage to the introducer. Transition to the
 * terminalState.</dd>
 * <dt>terminalState</dt>
 * <dd>This is the terminal state for the protocol. Unregister the protocol from
 * the node.</dd>
 * </dl>
 * 
 * @author Greg Frazier
 *
 */
public class TargetProtocol extends IntroductionProtocol {

	protected static final Event GOTO_REFUSE_STATE_EVENT = new Event() {
		private static final String NAME = "goto sendRefuseState";

		public String toString() {
			return NAME;
		}
	};

	protected static final Event GOTO_TERMINAL_STATE = new Event() {
		private static final String NAME = "goto terminalState";

		public String toString() {
			return NAME;
		}
	};

	protected static final Event GOTO_CREATE_VPN = new Event() {
		private static final String NAME = "goto createVPNState";

		public String toString() {
			return NAME;
		}
	};

	private IntroductionOfferMessage introductionOffer;

	public TargetProtocol(Node target, IntroductionOfferMessage m, boolean verbose) {
		super(target, m.getIntroductionRequest(), "Target Protocol", verbose);
		this.introductionOffer = m;
		setStartState(decideToAcceptState);
		// decide to not accept the introduction
		addTransition(
				new Transition(decideToAcceptState, GOTO_REFUSE_STATE_EVENT.getClass(), sendIntroductionRefusedState));
		// decided to accept, failed to use an existing VPN
		addTransition(new Transition(decideToAcceptState, FAILURE_EVENT.getClass(), createVPNState));
		// decided to accept, using an existing VPN
		addTransition(new Transition(decideToAcceptState, SUCCESS_EVENT.getClass(), sendIntroductionCompletedState));
		// decided to accept, so create a VPN
		addTransition(new Transition(decideToAcceptState, GOTO_CREATE_VPN.getClass(), createVPNState));

		// Reusing an existing VPN, creating a new VPN, or refusing the offer all end
		// with this node sending a message to the introducer. There is no recourse for
		// failure---whether the transmission succeeds or fails, after the transmission
		// we go to the terminalState, where the protocol is unregistered from the node.
		//
		addTransition(new Transition(sendIntroductionRefusedState, FAILURE_EVENT.getClass(), terminalState));
		addTransition(new Transition(sendIntroductionRefusedState, SUCCESS_EVENT.getClass(), terminalState));
		addTransition(new Transition(sendIntroductionCompletedState, FAILURE_EVENT.getClass(), terminalState));
		addTransition(new Transition(sendIntroductionCompletedState, SUCCESS_EVENT.getClass(), terminalState));
		addTransition(new Transition(createVPNState, FAILURE_EVENT.getClass(), terminalState));
		addTransition(new Transition(createVPNState, SUCCESS_EVENT.getClass(), terminalState));
	}

	/**
	 * Decide whether to accept the introductionRequest and see if the VPN already
	 * exists. If we are not going to accept the introductionRequest, go to
	 * refuseState. If the VPN does not exist, go to createVPNState. If the VPN
	 * exists, send the AddIntroductionRequestMessage and wait for success or
	 * failure. Success transitions to the sendIntroductionCompleted state. Failure
	 * transitions to the createVPNState.
	 */
	private static Action decideToAcceptAction = new Action() {
		@Override
		public void act(StateMachine sm, State state, Event event) {
			TargetProtocol rop = (TargetProtocol) sm;
			Pedigree p = rop.introductionOffer.getPedigree();
			// validate correctness
			if (!p.getSubject().equals(rop.introductionRequest.requester)) {
				rop.node.getLogger().severe(
						this + ": pedigree subject is not the requester. p=" + p + ", ir=" + rop.introductionRequest);
				rop.receive(GOTO_REFUSE_STATE_EVENT);
				return;
			}
			// validate correctness
			if (!rop.introductionOffer.getSrc().equals(rop.introductionRequest.introducer)) {
				rop.node.getLogger().severe(this + ": offer did not come from the introducer. offer="
						+ rop.introductionOffer + ", ir=" + rop.introductionRequest);
				rop.receive(GOTO_REFUSE_STATE_EVENT);
				return;
			}
			p = p.getNext(rop.introductionOffer.getIntroductionRequest());
			if (!rop.node.evaluatePedigree(p)) {
				rop.receive(GOTO_REFUSE_STATE_EVENT);
				return;
			}

			// We are going to accept the introduction, either by reusing an existing VPN or
			// by creating a new one. So, add this introduction request to the pending
			// feedbacks.
			rop.node.addPendingFeedbackToSend(rop.introductionRequest);

			// Now let's see if we are re-using a VPN.
			if (rop.node.addIntroductionRequestToVPN(rop.introductionRequest, rop.introductionRequest.requester)) {
				rop.node.send(rop, new AddIntroductionRequestMessage(rop.introductionRequest.requester,
						rop.node.getAddress(), rop.introductionRequest));
				// Do not transition out of this state; wait for the outcome of the send to
				// dictate the next state.
				return;
			}
			// ...else create the VPN and send the accept message
			rop.receive(GOTO_CREATE_VPN);
		}

	};

	private static State decideToAcceptState = new State("decide whether to accept", decideToAcceptAction);

	/**
	 * <dt>createVPNState</dt>
	 * <dd>Create the VPN. If this fails (!?), transition to the sendRefuseState.
	 * Otherwise, send the IntroductionAcceptedMessage. If this fails, transition to
	 * the removeVPNState. If this succeeds, transition to the terminalState.</dd>
	 */
	private static Action createVPNAction = new Action() {

		@Override
		public void act(StateMachine sm, State s, Event e) {
			TargetProtocol rop = (TargetProtocol) sm;
			Object keyingMaterial = rop.node.generateKeyingMaterial();
			boolean success = rop.node.createVPN(rop.introductionRequest.requester, rop.introductionRequest,
					keyingMaterial);
			if (!success) {
				rop.receive(GOTO_REFUSE_STATE_EVENT);
				return;
			}
			rop.node.send(rop,
					new IntroductionAcceptedMessage(rop.introductionRequest, keyingMaterial, rop.node.getAddress()));
			return;
		}

	};

	private static State createVPNState = new State("create VPN", createVPNAction);

	/**
	 * <dt>removeVPNState</dt>
	 * <dd>Remove the introductionRequest from the VPN. Transition to the
	 * terminalState.</dd>
	 */
	private static Action removeVPNAction = new Action() {

		@Override
		public void act(StateMachine sm, State s, Event e) {
			TargetProtocol rop = (TargetProtocol) sm;
			rop.node.removeIntroductionRequestFromVPN(rop.introductionRequest, rop.introductionRequest.requester);
			rop.receive(GOTO_TERMINAL_STATE);
		}

	};
	private static State removeVPNState = new State("remove VPN", removeVPNAction);

	/**
	 * <dt>sendIntroductionCompletedState</dt>
	 * <dd>Send the IntroductionCompletedMessage to the introducer, signaling that
	 * the VPN already exists. Transition to the terminalState.
	 */
	private static Action sendIntroductionCompletedAction = new Action() {

		@Override
		public void act(StateMachine sm, State s, Event e) {
			TargetProtocol rop = (TargetProtocol) sm;
			rop.node.send((IntroductionProtocol) null, new IntroductionCompletedMessage(rop.introductionRequest,
					/* no keying material */ null, rop.introductionRequest.introducer));
			rop.receive(GOTO_TERMINAL_STATE);
		}

	};
	private static State sendIntroductionCompletedState = new State("send completed", sendIntroductionCompletedAction);

	/**
	 * <dd>Send an IntroductionRefusedMessage to the introducer. Transition to the
	 * terminalState.</dd>
	 */
	private static Action sendIntroductionRefusedAction = new Action() {

		@Override
		public void act(StateMachine sm, State s, Event e) {
			TargetProtocol rop = (TargetProtocol) sm;
			rop.node.send((IntroductionProtocol) null,
					new IntroductionRefusedMessage(rop.introductionRequest, rop.introductionRequest.introducer));
			rop.receive(GOTO_TERMINAL_STATE);
		}

	};
	private static State sendIntroductionRefusedState = new State("send refusal", sendIntroductionRefusedAction);

	/**
	 * <dt>terminalState</dt>
	 * <dd>This is the terminal state for the protocol. Unregister the protocol from
	 * the node.</dd>
	 */
	private static Action terminalAction = new Action() {

		@Override
		public void act(StateMachine sm, State s, Event e) {
			TargetProtocol rop = (TargetProtocol) sm;
			rop.node.unregisterProtocol(rop);
		}

	};
	private static State terminalState = new State("terminal state", terminalAction);
}
