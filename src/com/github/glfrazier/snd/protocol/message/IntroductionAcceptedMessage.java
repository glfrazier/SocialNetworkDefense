package com.github.glfrazier.snd.protocol.message;

import static com.github.glfrazier.snd.protocol.IntroductionRequest.SAMPLE_INTRODUCTION_REQUEST;
import java.io.Serializable;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.protocol.IntroductionRequest;

public class IntroductionAcceptedMessage extends IntroductionMessage implements Serializable, Event {

	/**
	 * 
	 */
	public static final IntroductionAcceptedMessage INTRODUCTION_ACCEPTED = //
			new IntroductionAcceptedMessage(SAMPLE_INTRODUCTION_REQUEST, null);

	private static final long serialVersionUID = 1L;

	public IntroductionAcceptedMessage(IntroductionRequest req, InetAddress target) {
		super(req.introducer, target, req, MessageType.INTRODUCTION_ACCEPTED);
	}

}
