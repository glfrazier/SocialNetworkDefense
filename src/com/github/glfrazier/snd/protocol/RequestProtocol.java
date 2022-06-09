package com.github.glfrazier.snd.protocol;

import java.io.IOException;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.protocol.message.IntroductionCompletedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionDeniedWillRouteMessage;
import com.github.glfrazier.snd.protocol.message.IntroductionRequestMessage;
import com.github.glfrazier.snd.util.Communicator;
import com.github.glfrazier.snd.util.VPN;
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
 * owner can call {@link #vpnWasCreated()} to see if a VPN was created and
 * {@link #getResultingVPN()} to get the VPN, or {@link #routeIsAvailable()} to
 * see if a route is available and {@link #getIntroducer()} to get the IP
 * address of the node that will route to the destination.
 */
public class RequestProtocol extends StateMachine {

	private final Communicator requester;
	private final IntroductionRequest request;

	private VPN resultingVPN;
	private State successState;
	private State routeAvailableState;

	public static final Event TIMEOUT_EVENT = new EventImpl<>(TIMEOUT);
	public static final Event FAILURE_EVENT = new EventImpl<>("FAILURE");

	public RequestProtocol(Communicator requester, IntroductionRequest request) {
		super("RequestProtocol:" + request, EventEqualityMode.CLASS_EQUALS);
		//this.verbose = true;
		this.requester = requester;
		this.request = request;
		State sendRequestState = new State("Send Request", createIntroductionRequestAction(this));
		setStartState(sendRequestState);
		State failureState = new State("Failure");
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

	private Communicator getRequester() {
		return requester;
	}

	public InetAddress getIntroducer() {
		return request.introducer;
	}

	public boolean vpnWasCreated() {
		return this.getCurrentState() == successState;
	}

	public boolean routeIsAvailable() {
		return this.getCurrentState() == routeAvailableState;
	}

	public VPN getResultingVPN() {
		return resultingVPN;
	}

	public static State.Action createIntroductionRequestAction(RequestProtocol sm) {
		return new State.Action() {
			@Override
			public void act(State s, Event e) {
				IntroductionRequestMessage req = new IntroductionRequestMessage(sm.request);
				VPN vpn = sm.getRequester().getVPN(req.getIntroductionRequest().introducer);
				try {
					vpn.send(req);
				} catch (IOException e1) {
					sm.receive(FAILURE_EVENT);
				}
			}
		};
	}

	private static State.Action createIntroductionSuccessAction(RequestProtocol sm) {

		return new State.Action() {
			@Override
			public void act(State s, Event e) {
				IntroductionCompletedMessage successMsg = (IntroductionCompletedMessage) e;
				InetAddress n = successMsg.getNextStep();
				VPN vpn = null;
				try {
					vpn = sm.getRequester().openVPN(n, sm.request);
				} catch (IOException exn) {
					System.err.println("Exception msg: " + exn + " -- Did we already have a VPN open!?");
					exn.printStackTrace();
					System.exit(-1);
				}
				sm.resultingVPN = vpn;
			}
		};
	}

	public IntroductionRequest getIntroductionRequest() {
		return request;
	}
}
