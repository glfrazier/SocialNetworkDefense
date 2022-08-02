package com.github.glfrazier.snd.protocol;

import com.github.glfrazier.snd.node.SNDNode;
import com.github.glfrazier.snd.protocol.message.IntroductionOfferMessage;
import com.github.glfrazier.statemachine.StateMachine;
import com.github.glfrazier.statemachine.StateMachine.EventEqualityMode;

public class IntroductionProtocol extends StateMachine {

	protected final SNDNode node;
	protected final IntroductionRequest introductionRequest;

	public IntroductionProtocol(SNDNode sndNode, IntroductionRequest ir, String label, boolean verbose) {
		super(label + ": " + ir, EventEqualityMode.CLASS_EQUALS, sndNode.getEventingSystem());
		this.node = sndNode;
		this.introductionRequest = ir;
		this.verbose = verbose;
	}
	
	public IntroductionRequest getIntroductionRequest() {
		return introductionRequest;
	}
	
	public SNDNode getNode() {
		return node;
	}
}
