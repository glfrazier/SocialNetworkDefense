package com.github.glfrazier.snd.protocol;

import java.io.IOException;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.node.SNDNode;
import com.github.glfrazier.snd.protocol.message.IntroductionCompletedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedWillRouteMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRequestMessage;
import com.github.glfrazier.statemachine.EventImpl;
import com.github.glfrazier.statemachine.State;
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
 * <dd>A terminal state. Receiving a Introduction Denied, Route Available
 * message in the sendRequestState transitions the state machine to the
 * routeAvailableState.</dd>
 * </dl>
 * The owner of this state machine creates a UUID for the introduction, and then
 * creates this state machine and registers itself as a listener
 * ({@link #registerCallback(StateMachineTracker)}). Any incoming SND messages
 * that have this UUID are passed to the {@link #receive(Event)} method. When
 * {@link StateMachineTracker#stateMachineEnded(StateMachine)} is invoked, the
 * owner can call {@link #introductionSucceeded()} to see if a VPN was created and
 * {@link #getResultingNeighbor()} to get the VPN, or {@link #routeIsAvailable()} to
 * see if a route is available and {@link #getIntroducer()} to get the IP
 * address of the node that will route to the destination.
 */
public class RequestProtocol extends StateMachine {

	private final SNDNode requester;
	private final IntroductionRequest request;
	private InetAddress target;

	private State successState;
	private State routeAvailableState;

	public static final Event TIMEOUT_EVENT = new EventImpl<>(TIMEOUT);
	public static final Event FAILURE_EVENT = new EventImpl<>("FAILURE");

	public RequestProtocol(SNDNode requester, IntroductionRequest request, boolean verbose) {
		super("RequestProtocol:" + request, EventEqualityMode.CLASS_EQUALS);
		this.verbose = verbose;
		this.requester = requester;
		this.request = request;
		State sendRequestState = new State("Send Request", createIntroductionRequestAction(this));
		setStartState(sendRequestState);
		State failureState = new State("Failure", createIntroductionFailedAction(this));
		Transition t = new Transition(sendRequestState, TIMEOUT_EVENT, failureState);
		addTransition(t);
		t = new Transition(sendRequestState, IntroductionDeniedMessage.SAMPLE_INTRODUCTION_DENIED, failureState);
		addTransition(t);
		successState = new State("Success", createIntroductionSuccessAction(this));
		t = new Transition(sendRequestState, IntroductionCompletedMessage.SAMPLE_INTRODUCTION_SUCCESS, successState);
		addTransition(t);
		routeAvailableState = new State("Route Available");
		t = new Transition(sendRequestState, IntroductionDeniedWillRouteMessage.SAMPLE_INTRODUCTION_DENIED_WILL_ROUTE,
				routeAvailableState);
		addTransition(t);
	}

	public InetAddress getIntroducer() {
		return request.introducer;
	}

	public boolean introductionSucceeded() {
		return this.getCurrentState() == successState;
	}

	public boolean routeIsAvailable() {
		return this.getCurrentState() == routeAvailableState;
	}

	public InetAddress getResultingNeighbor() {
		return target;
	}

	public State.Action createIntroductionRequestAction(RequestProtocol requestProtocol) {
		return new State.Action() {
			@Override
			public void act(State s, Event e) {
				IntroductionRequestMessage req = new IntroductionRequestMessage(requestProtocol.request);
				try {
					requester.router.send(req);
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
					requestProtocol.receive(FAILURE_EVENT);
				}
			}
		};
	}

	private State.Action createIntroductionSuccessAction(RequestProtocol requestProtocol) {

		return new State.Action() {
			@Override
			public void act(State s, Event e) {
				IntroductionCompletedMessage successMsg = (IntroductionCompletedMessage) e;
				target = successMsg.getNewNeighbor();
				try {
					requester.router.openIntroducedLink(target, request, successMsg.getKeyingMaterial());
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		};
	}

	private State.Action createIntroductionFailedAction(RequestProtocol requestProtocol) {

		return new State.Action() {
			@Override
			public void act(State s, Event e) {
				requestProtocol.requester.getLogger().warning(this + ": " + request + " DENIED");
			}
		};
	}

	public IntroductionRequest getIntroductionRequest() {
		return request;
	}
}
