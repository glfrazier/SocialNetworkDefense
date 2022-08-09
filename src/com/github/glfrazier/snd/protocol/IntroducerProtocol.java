package com.github.glfrazier.snd.protocol;

import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.node.Node;
import com.github.glfrazier.snd.protocol.message.IntroductionAcceptedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionCompletedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionOfferMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRefusedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRequestMessage;
import com.github.glfrazier.statemachine.State;
import com.github.glfrazier.statemachine.State.Action;
import com.github.glfrazier.statemachine.StateMachine;
import com.github.glfrazier.statemachine.Transition;

public class IntroducerProtocol extends IntroductionProtocol {

	private boolean introductionSucceeded;

	public IntroducerProtocol(Node target, IntroductionRequestMessage m, boolean verbose) {
		super(target, m.getIntroductionRequest(), "Introducer Protocol", verbose);

		setStartState(decisionState);
		addTransition(new Transition(decisionState, FAILURE_EVENT.getClass(), sendDeniedState));
		addTransition(new Transition(decisionState, IntroductionRefusedMessage.class, sendDeniedState));
		addTransition(new Transition(decisionState, IntroductionCompletedMessage.class, terminalState));
		addTransition(new Transition(decisionState, IntroductionAcceptedMessage.class, sendCompletionState));
		addTransition(new Transition(sendDeniedState, WILDCARD_EVENT.getClass(), terminalState));
		addTransition(new Transition(sendCompletionState, WILDCARD_EVENT.getClass(), terminalState));

	}

	private static final Action decisionAction = new Action() {

		@Override
		public void act(StateMachine sm, State s, Event e) {
			IntroducerProtocol rrp = (IntroducerProtocol) sm;
			Pedigree p = rrp.node.getPedigree(rrp.introductionRequest.requester);
			boolean sendOffer = rrp.node.evaluatePedigree(p);
			if (sendOffer) {
				InetAddress target = rrp.node.getNextHopTo(rrp.introductionRequest.destination);
				rrp.node.send(rrp, new IntroductionOfferMessage(rrp.introductionRequest, target, p));
			} else {
				sm.receive(FAILURE_EVENT);
			}
		}

	};
	private static final State decisionState = new State("decide to offer", decisionAction);
	
	private static final Action sendDeniedAction = new Action() {

		@Override
		public void act(StateMachine sm, State s, Event e) {
			IntroducerProtocol rrp = (IntroducerProtocol) sm;
			rrp.node.send(rrp, new IntroductionDeniedMessage(rrp.introductionRequest));
		}

	};
	private static final State sendDeniedState = new State("send IntroductionDeniedMessage", sendDeniedAction);
	
	private static final Action sendCompletionAction = new Action() {

		@Override
		public void act(StateMachine sm, State s, Event e) {
			IntroducerProtocol rrp = (IntroducerProtocol) sm;
			IntroductionAcceptedMessage iaMsg = (IntroductionAcceptedMessage) e;
			rrp.introductionSucceeded = true;
			rrp.node.send(rrp, new IntroductionCompletedMessage(rrp.introductionRequest, iaMsg.getKeyingMaterial(),
					iaMsg.getSrc()));
		}

	};
	private static final State sendCompletionState = //
			new State("send IntroductionCompletedMessage", sendCompletionAction);

	private static final Action terminalAction = new Action() {

		@Override
		public void act(StateMachine sm, State s, Event e) {
			IntroducerProtocol rrp = (IntroducerProtocol) sm;
			if (e instanceof IntroductionCompletedMessage) {
				rrp.introductionSucceeded = true;
			}
			if (rrp.introductionSucceeded) {
				rrp.node.addPendingFeedbackToReceive(rrp.getIntroductionRequest());
			}
			rrp.node.unregisterProtocol(rrp);
		}

	};
	private static final State terminalState = new State("terminal state", terminalAction);

}
