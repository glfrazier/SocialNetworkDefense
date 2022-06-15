package com.github.glfrazier.snd.protocol;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.node.ClientProxy;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.util.VPN;
import com.github.glfrazier.statemachine.State;
import com.github.glfrazier.statemachine.State.Action;
import com.github.glfrazier.statemachine.StateMachine;
import com.github.glfrazier.statemachine.Transition;

/**
 * One start state ("Unconnected"), two end states ("Connected", "Failure"). In
 * Unconnected, the action is to kick off an {@link RequestProtocol} toward the
 * destination, via the current {@link #introducer}. If the introduction
 * succeeds, the old VPN is closed. If the new VPN is to the message's
 * destination we send the message and transition to Connected.
 * 
 * Note that the message still needs to be sent when this protocol is completed.
 * 
 * @author Greg Frazier
 *
 */
public class ClientConnectionEngine
//extends StateMachine implements StateMachine.StateMachineTracker
{
/*
	public static enum Outcome {
		FAILURE, CONNECTED_TO_DESTINATION, CONNECTED_TO_ROUTER
	};

	private static final Event FAILURE = new Event() {
		public String toString() {
			return "request was denied or timed out";
		}
	};
	private static final Event WILL_ROUTE = new Event() {
		public String toString() {
			return "will route";
		}
	};
	private static final Event INTRO_COMPLETED = new Event() {
		public String toString() {
			return "introduction completed";
		}
	};
	private static final Event CONNECTED_TO_DESTINATION = new Event() {
		public String toString() {
			return "connected to destination";
		}
	};
	private static final Event NOT_YET_CONNECTED = new Event() {
		public String toString() {
			return "begin next iteration";
		}
	};
	private Message message;
	private ClientProxy requester;

	private State startState;
	private State requestIntroductionState;
	private State assessCompletionState;
	private State nextIterationState;
	private State failureState;
	private State successState;

	private VPN vpn;
	private IntroductionRequest introductionToIntroducer;
	private VPN resultVPN;
	private IntroductionRequest introductionRequest;

	public ClientConnectionEngine(ClientProxy client, Message m) {
		super("Introduction Sequence: " + m.getSrc() + " ==> " + m.getDst(), EventEqualityMode.CLASS_EQUALS);
		this.setVerbose(true);
		this.message = m;
		this.requester = client;
		startState = new State("START", createStartAction(this));
		requestIntroductionState = new State("REQUEST INTRODUCTION", createRequestIntroductionAction(this));
		assessCompletionState = new State("ASSESS COMPLETION", createAssessComplectionAction());
		failureState = new State("FAILURE", createFailureAction());
		successState = new State("SUCCESS", createSuccessAction());
		nextIterationState = new State("PREPARE NEXT ITERATION", createNextIterationAction());
		this.setStartState(startState);
		this.addTransition(new Transition(startState, null, requestIntroductionState));
		this.addTransition(new Transition(requestIntroductionState, FAILURE, failureState));
		this.addTransition(new Transition(requestIntroductionState, WILL_ROUTE, successState));
		this.addTransition(new Transition(requestIntroductionState, INTRO_COMPLETED, assessCompletionState));
		this.addTransition(new Transition(assessCompletionState, CONNECTED_TO_DESTINATION, successState));
		this.addTransition(new Transition(assessCompletionState, NOT_YET_CONNECTED, nextIterationState));
		this.addTransition(new Transition(nextIterationState, null, requestIntroductionState));
	}

	private Action createNextIterationAction() {

		return new Action() {

			@Override
			public void act(State s, Event e) {
				requester.getImplementation().getComms().closeIntroducedVPN(introducer);
			}
		};
	}


	private Action createSuccessAction() {
		return new Action() {

			@Override
			public void act(State s, Event e) {
				resultVPN = vpn;
			}
		};
	}

	private Action createFailureAction() {
		return new Action() {

			@Override
			public void act(State s, Event e) {
				closeIntroductionVPN();
			}

		};
	}

	private Action createAssessComplectionAction() {
		return new Action() {

			@Override
			public void act(State s, Event e) {
				if (vpn.getRemote().equals(message.getDst())) {
					receive(CONNECTED_TO_DESTINATION);
				} else {
					receive(NOT_YET_CONNECTED);
				}
			}
		};
	}

	private Action createRequestIntroductionAction(ClientConnectionEngine clientConnectionEngine) {
		return new Action() {

			@Override
			public void act(State s, Event e) {
				IntroductionRequest ir = new IntroductionRequest(requester.getAddress(), vpn.getRemote(),
						message.getDst());
				RequestProtocol rp = new RequestProtocol(requester, ir);
				requester.registerProtocol(rp);
				rp.registerCallback(clientConnectionEngine);
				rp.begin();
				// The rest of this action is implemented in stateMachineEnded(StateMachine)
			}
		};
	}

	@Override
	public void stateMachineEnded(StateMachine machine) {
		RequestProtocol rp = (RequestProtocol) machine;
		if (rp.introductionSucceeded()) {
			closeIntroductionVPN();
			introductionRequest = rp.getIntroductionRequest();
			vpn = rp.getResultingNeighbor();
			receive(INTRO_COMPLETED);
		}
		if (rp.routeIsAvailable()) {
			receive(WILL_ROUTE);
		}
		// else timeout or denied
		receive(FAILURE);
	}

	private Action createStartAction(ClientConnectionEngine clientConnectionEngine) {
		return new Action() {

			@Override
			public void act(State s, Event e) {
				vpn = requester.getVPN(requester.getInitialIntroducer());
			}
		};
	}

	public Outcome getOutcome() {
		if (this.getCurrentState() == failureState) {
			return Outcome.FAILURE;
		}
		if (this.getCurrentState() == successState) {
			if (resultVPN.getRemote() == message.getDst()) {
				return Outcome.CONNECTED_TO_DESTINATION;
			} else {
				return Outcome.CONNECTED_TO_ROUTER;
			}
		}
		throw new IllegalStateException(this + " is not in a terminal state.");
	}

	public VPN getResultingVPN() {
		return resultVPN;
	}
*/
}