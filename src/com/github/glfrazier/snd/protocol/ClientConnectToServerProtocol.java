package com.github.glfrazier.snd.protocol;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;

import java.io.IOException;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.node.ClientProxy;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.util.DenialReporter;
import com.github.glfrazier.statemachine.EventImpl;
import com.github.glfrazier.statemachine.State;
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
public class ClientConnectToServerProtocol extends StateMachine implements StateMachine.StateMachineTracker {

	private static final Event NEXT_STEP = new EventImpl<RequestProtocol>(null, "next_step");
	private static final Event CONNECTED = new EventImpl<RequestProtocol>(null, "connected");
	private static final Event FAILURE = new EventImpl<RequestProtocol>(null, "failure");
	private Message message;
	private ClientProxy requester;
	private InetAddress introducer;

	private IntroductionRequest priorIntroduction;

	private DenialReporter denialReporter;

	private State unconnectedState;
	private State connectedState;
	private State failureState;

	private int depth = 0;

	public ClientConnectToServerProtocol(ClientProxy client, Message m, DenialReporter denialReporter,
			boolean verbose) {
		super("Introduction Sequence: " + addrToString(m.getSrc()) + " ==> " + addrToString(m.getDst()),
				EventEqualityMode.EQUALS);
		this.message = m;
		this.requester = client;
		this.denialReporter = denialReporter;
		this.verbose = verbose;
		unconnectedState = new State("Unconnected", createIntroductionAction(this));
		addTransition(new Transition(unconnectedState, NEXT_STEP, unconnectedState));
		setStartState(unconnectedState);
		failureState = new State("Failure");
		addTransition(new Transition(unconnectedState, FAILURE, failureState));
		connectedState = new State("Connected");
		addTransition(new Transition(unconnectedState, CONNECTED, connectedState));
		introducer = requester.getInitialIntroducer();
	}

	public boolean isCompleted() {
		return this.getCurrentState() == failureState || this.getCurrentState() == connectedState;
	}

	public Message getMessage() {
		return message;
	}

	private State.Action createIntroductionAction(ClientConnectToServerProtocol clientToServerProtocol) {
		return new State.Action() {

			public void act(State state, Event e) {
				IntroductionRequest request = new IntroductionRequest(requester.getAddress(), introducer,
						message.getDst());
				RequestProtocol intro = new RequestProtocol(requester, request, verbose);
				requester.registerProtocol(intro);
				intro.registerCallback(clientToServerProtocol);
				intro.begin();
			}
		};
	}

	@Override
	public void stateMachineEnded(StateMachine machine) {
		RequestProtocol rp = (RequestProtocol) machine;
		if (rp.introductionSucceeded()) {
			InetAddress newNeighbor = rp.getResultingNeighbor();
			try {
				if (priorIntroduction != null) {
					requester.getImplementation().getComms().closeIntroducedVPN(rp.getIntroducer(), priorIntroduction);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (newNeighbor.equals(message.getDst())) {
				try {
					requester.getImplementation().getComms().send(message);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				requester.addIntroductionToDestionation(message.getDst(), message.getSrc(),
						rp.getIntroductionRequest());
				this.receive(CONNECTED);
			} else {
				introducer = newNeighbor;
				priorIntroduction = rp.getIntroductionRequest();
				depth++;
				this.receive(NEXT_STEP);
			}
			return;
		} // else
		if (rp.routeIsAvailable()) {
			requester.getImplementation().getComms().addRoute(message.getDst(), rp.getIntroductionRequest().introducer);
			try {
				requester.getImplementation().getComms().send(message);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			requester.addIntroductionToDestionation(message.getDst(), message.getSrc(), rp.getIntroductionRequest());
			this.receive(CONNECTED);
			return;
		}
		// else
		denialReporter.deniedAtDepth(depth);
		this.receive(FAILURE);
		return;

	}

	public boolean isConnected() {
		return this.getCurrentState() == connectedState;
	}

}
