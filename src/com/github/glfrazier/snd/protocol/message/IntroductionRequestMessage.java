package com.github.glfrazier.snd.protocol.message;

import java.io.Serializable;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.statemachine.StateMachine;

/**
 * A message sent from a Client to an Introducer, requesting an Introduction to
 * (toward) the destination.
 * 
 * Implementation Notes:
 * <ul>
 * <li>This class implements {@link Event}. That is because the SND
 * implementation uses {@link StateMachine} to implement the protocol, and state
 * machines take Events as inputs.</li>
 * <li>The class offers a constant instance
 * {@link #SAMPLE_INTRODUCTION_REQUEST}. The intent is that the constant can be
 * used when constructing a state machine to process the protocol. All SDN
 * messages offer a constant instance for this reason.
 * <ul>
 * 
 * @author Greg Frazier
 *
 */
public class IntroductionRequestMessage extends IntroductionMessage implements Serializable, Event {

	private static final long serialVersionUID = 1L;
	
	private final IntroductionRequest previousIR;

	public IntroductionRequestMessage(IntroductionRequest req, IntroductionRequest previousReq) {
		super(req.introducer, req.requester, req, MessageType.INTRODUCTION_REQUEST);
		previousIR = previousReq;
	}
	
	public IntroductionRequest getPreviousIntroductionRequest() {
		return previousIR;
	}

}
