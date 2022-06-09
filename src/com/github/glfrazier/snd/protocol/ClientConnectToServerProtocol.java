package com.github.glfrazier.snd.protocol;

import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.node.ClientProxy;
import com.github.glfrazier.snd.protocol.message.ClientAppMessage;
import com.github.glfrazier.snd.util.VPN;
import com.github.glfrazier.statemachine.EventImpl;
import com.github.glfrazier.statemachine.State;
import com.github.glfrazier.statemachine.StateMachine;
import com.github.glfrazier.statemachine.Transition;

/**
 * One start state ("Unconnected"), two end states ("Connected", "Failure"). In
 * Unconnected, the action is to kick off an {@link RequestProtocol} toward
 * the destination, via the current {@link #introducer}. If the introduction
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
	private ClientAppMessage message;
	private ClientProxy requester;
	private IntroductionRequest request;
	private VPN nextVPN;
	
	private State unconnectedState;
	private State connectedState;
	private State failureState;
	
	private InetAddress proxyAddress;

	public ClientConnectToServerProtocol(ClientProxy client, ClientAppMessage m) {
		super("Introduction Sequence: " + m.getSrc() + " ==> " + m.getDst(), EventEqualityMode.CLASS_EQUALS);
		this.message = m;
		this.requester = client;
		this.nextVPN = client.getVPN(client.getInitialIntroducer());
		unconnectedState = new State("Unconnected", createIntroductionAction(this));
		addTransition(new Transition(unconnectedState, NEXT_STEP, unconnectedState));
		setStartState(unconnectedState);
		failureState = new State("Failure");
		addTransition(new Transition(unconnectedState, FAILURE, failureState));
		connectedState = new State("Connected");
		addTransition(new Transition(unconnectedState, CONNECTED, connectedState));
	}

	public boolean isCompleted() {
		return this.getCurrentState() == failureState || this.getCurrentState() == connectedState;
	}

	public VPN getServerVPN() {
		if (this.getCurrentState() != connectedState) {
			throw new IllegalStateException(this + " is not in the connected state.");
		}
		return nextVPN;
	}

	public ClientAppMessage getMessage() {
		return message;
	}

	private State.Action createIntroductionAction(ClientConnectToServerProtocol clientToServerProtocol) {
		return new State.Action() {

			public void act(State state, Event e) {
				request = new IntroductionRequest(clientToServerProtocol.getClient().getAddress(),
						nextVPN.getRemote(), clientToServerProtocol.getMessage().getDst());
				RequestProtocol intro = new RequestProtocol(requester, request);
				clientToServerProtocol.getClient().registerProtocol(intro);
				intro.registerCallback(clientToServerProtocol);
				intro.begin();
			}
		};
	}

	protected ClientProxy getClient() {
		return requester;
	}

	@Override
	public void stateMachineEnded(StateMachine machine) {
		RequestProtocol ip = (RequestProtocol) machine;
		if (!nextVPN.getRemote().equals(requester.getInitialIntroducer())) {
			requester.closeVPN(request);
		}
		if (ip.vpnWasCreated()) {
			nextVPN = ip.getResultingVPN();
			if (nextVPN.getRemote().equals(proxyAddress)) {
				this.receive(CONNECTED);
				return;
			}
			// else
			this.receive(NEXT_STEP);
		}
	}

	public boolean isConnected() {
		return this.getCurrentState() == connectedState;
	}

}
