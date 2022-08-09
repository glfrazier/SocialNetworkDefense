package com.github.glfrazier.snd.protocol;

import com.github.glfrazier.snd.node.Node;
import com.github.glfrazier.snd.protocol.message.IntroductionOfferMessage;
import com.github.glfrazier.statemachine.StateMachine;
import com.github.glfrazier.statemachine.StateMachine.EventEqualityMode;

public class IntroductionProtocol extends StateMachine {

	protected final Node node;
	protected final IntroductionRequest introductionRequest;

	public IntroductionProtocol(Node sndNode, IntroductionRequest ir, String label, boolean verbose) {
		super(label + "(" + sndNode + "): " + ir, EventEqualityMode.CLASS_EQUALS, sndNode.getEventingSystem());
		this.node = sndNode;
		this.introductionRequest = ir;
		this.verbose = verbose;
	}
	
	public IntroductionRequest getIntroductionRequest() {
		return introductionRequest;
	}
	
	public Node getNode() {
		return node;
	}
	
	public boolean getVerbose() {
		return verbose;
	}
}
