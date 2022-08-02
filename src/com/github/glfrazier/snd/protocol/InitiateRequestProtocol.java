package com.github.glfrazier.snd.protocol;

import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.node.SNDNode;
import com.github.glfrazier.snd.protocol.message.AddIntroductionRequestMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionCompletedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRequestMessage;
import com.github.glfrazier.statemachine.State;
import com.github.glfrazier.statemachine.State.Action;
import com.github.glfrazier.statemachine.StateMachine;
import com.github.glfrazier.statemachine.Transition;

/**
 * The finite state machine for requesting an introduction. There are four
 * states:
 * <dl>
 * <dt>sendRequestState</dt>
 * <dd>The initial state. The requester sends an introduction request to the
 * introducer and waits for a response.</dd>
 * <dt>successState</dt>
 * <dd>A terminal state. Receiving an Introduction Success message in the
 * sendRequestState transitions the state machine to the success state. In the
 * success state, the new VPN is created.</dd>
 * <dt>failureState</dt>
 * <dd>A terminal state. Receiving a timeout or an Introduction Denied message
 * in the sendRequestState transitions the state machine to the
 * failureState.</dd>
 * <dt>routeAvailableState</dt>
 * <dd>A terminal state. Receiving a Introduction Denied, Will Route message in
 * the sendRequestState transitions the state machine to the
 * routeAvailableState.</dd>
 * </dl>
 */
public class InitiateRequestProtocol extends IntroductionProtocol {

	private InetAddress target;

	public InitiateRequestProtocol(SNDNode requester, IntroductionRequest request, boolean verbose) {
		super(requester, request, "Initiate Request Protocol", verbose);
		
		setStartState(sendRequestState);
		addTransition(new Transition(sendRequestState, FAILURE_EVENT.getClass(), failureState));
		addTransition(new Transition(sendRequestState, IntroductionDeniedMessage.class, failureState));
		addTransition(new Transition(sendRequestState, IntroductionCompletedMessage.class, successState));
		addTransition(new Transition(sendRequestState, AddIntroductionRequestMessage.class, successState));
	}

	private static final Action waiteForResponseAction = new Action() {
		@Override
		public void act(StateMachine sm, State s, Event e) {
			if (e == null) {
				return;
			}
		}
	};

	public InetAddress getIntroducer() {
		return introductionRequest.introducer;
	}

	public boolean introductionSucceeded() {
		return this.getCurrentState() == successState;
	}

	public InetAddress getResultingNeighbor() {
		return target;
	}

	public static final Action introductionRequestAction = new State.Action() {
		@Override
		public void act(StateMachine sm, State s, Event e) {
			InitiateRequestProtocol irp = (InitiateRequestProtocol) sm;
			IntroductionRequestMessage req = new IntroductionRequestMessage(irp.introductionRequest);
			irp.node.send(irp, req);
		}
	};

	private static final Action introductionSuccessAction = new State.Action() {
		@Override
		public void act(StateMachine sm, State s, Event e) {
			InitiateRequestProtocol irp = (InitiateRequestProtocol) sm;
			Object keyingMaterial = null;
			if (e instanceof IntroductionCompletedMessage) {
				IntroductionCompletedMessage successMsg = (IntroductionCompletedMessage) e;
				irp.target = successMsg.getNewNeighbor();
				keyingMaterial = successMsg.getKeyingMaterial();
				irp.node.createVPN(irp.target, irp.introductionRequest, keyingMaterial);
			}
			if (e instanceof AddIntroductionRequestMessage) {
				AddIntroductionRequestMessage airm = (AddIntroductionRequestMessage)e;
				irp.target = airm.getSrc();
			}
			irp.node.unregisterProtocol(irp);
		}
	};

	private static final Action introductionFailedAction = new Action() {
		@Override
		public void act(StateMachine sm, State s, Event e) {
			InitiateRequestProtocol irp = (InitiateRequestProtocol) sm;
			irp.node.unregisterProtocol(irp);
			((InitiateRequestProtocol) sm).node.getLogger()
					.warning(this + ": " + ((InitiateRequestProtocol) sm).introductionRequest + " DENIED");
		}
	};

	private static final State sendRequestState = new State("Send Request", introductionRequestAction);
	private static final State successState = new State("Success", introductionSuccessAction);
	private static final State failureState = new State("Failure", introductionFailedAction);

}
