package com.github.glfrazier.snd.protocol;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;

import java.io.IOException;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.node.ProxyNode;
import com.github.glfrazier.snd.node.Node;
import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.util.DenialReporter;
import com.github.glfrazier.statemachine.EventImpl;
import com.github.glfrazier.statemachine.State;
import com.github.glfrazier.statemachine.StateMachine;
import com.github.glfrazier.statemachine.Transition;

/**
 * One start state ("Unconnected"), two end states ("Connected", "Failure"). In
 * Unconnected, the action is to kick off an {@link RequesterProtocol}
 * toward the destination, via the current {@link #introducer}. If the
 * introduction succeeds, the old VPN is closed. If the new VPN is to the
 * message's destination we send the message and transition to Connected.
 * 
 * Note that the message still needs to be sent when this protocol is completed.
 * 
 * @author Greg Frazier
 *
 */
public class ClientConnectToServerProtocol extends StateMachine implements StateMachine.StateMachineTracker {

	private static final Event NEXT_STEP = new EventImpl<RequesterProtocol>(null, "next_step");
	private static final Event CONNECTED = new EventImpl<RequesterProtocol>(null, "connected");
	private static final Event FAILURE = new EventImpl<RequesterProtocol>(null, "failure");
	private final Message message;
	private final Node requester;
	private InetAddress introducer;
	private final InetAddress target;

	private IntroductionRequest priorIntroduction;
	private DenialReporter denialReporter;

	private int depth = 0;

	public ClientConnectToServerProtocol(ProxyNode node, Message m, InetAddress networkDestination, DenialReporter denialReporter,
			boolean verbose) {
		super("Introduction Sequence: " + addrToString(node.getAddress()) + " ==> " + addrToString(networkDestination),
				EventEqualityMode.EQUALS, node.getEventingSystem());
		this.message = m;
		this.requester = node;
		this.target = networkDestination;
		this.denialReporter = denialReporter;
		this.verbose = verbose;
		this.introducer = requester.getNextHopTo(m.getDst());

		addTransition(new Transition(unconnectedState, NEXT_STEP, unconnectedState));
		addTransition(new Transition(unconnectedState, FAILURE, failureState));
		addTransition(new Transition(unconnectedState, CONNECTED, connectedState));
		setStartState(unconnectedState);
	}

	public boolean isCompleted() {
		return this.getCurrentState() == failureState || this.getCurrentState() == connectedState;
	}

	public Message getMessage() {
		return message;
	}

	@Override
	public void stateMachineEnded(StateMachine machine) {
		RequesterProtocol requestProtocol = (RequesterProtocol) machine;
		if (requestProtocol.introductionSucceeded()) {
			InetAddress newNeighbor = requestProtocol.getResultingNeighbor();
			// Remove the prior introduction from the link used in the prior introduction. Note that this has no impact on a-priori
			// connections.
			if (priorIntroduction != null) {
				requester.removeIntroductionRequestFromVPN(priorIntroduction, requestProtocol.getIntroducer());
			}
			if (newNeighbor.equals(target)) {
				if (!message.getDst().equals(target)) {
					requester.addRoute(message.getDst(), target);
				}
				requester.send(message);
				this.receive(CONNECTED);
			} else {
				introducer = newNeighbor;
				priorIntroduction = requestProtocol.getIntroductionRequest();
				depth++;
				this.receive(NEXT_STEP);
			}
			return;
		}
		// else
		denialReporter.deniedAtDepth(depth);
		this.receive(FAILURE);
		return;
	}

	private static State.Action introductionAction = new State.Action() {

		public void act(StateMachine sm, State state, Event e) {
			ClientConnectToServerProtocol protocol = (ClientConnectToServerProtocol) sm;
			IntroductionRequest request = new IntroductionRequest(protocol.requester.getAddress(), protocol.introducer,
					protocol.target);
			protocol.verbose |=  protocol.requester.checkIntroductionRequestNonce(request.nonce);
			RequesterProtocol intro = new RequesterProtocol(protocol.requester, request, protocol.verbose);
			intro.registerCallback(protocol);
			intro.begin();
		}
	};

	private static State unconnectedState = new State("Unconnected", introductionAction);
	private static State connectedState = new State("Connected");
	private static State failureState = new State("Failure");

	public boolean isConnected() {
		return this.getCurrentState() == connectedState;
	}

}
