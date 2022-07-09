package com.github.glfrazier.snd.protocol.message;

import java.io.Serializable;
import static com.github.glfrazier.snd.protocol.IntroductionRequest.SAMPLE_INTRODUCTION_REQUEST;
import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.protocol.IntroductionRequest;

public class IntroductionDeniedMessage extends IntroductionMessage implements Serializable, Event {

	/**
	 * 
	 */
	public static final IntroductionDeniedMessage SAMPLE_INTRODUCTION_DENIED = new IntroductionDeniedMessage(
			SAMPLE_INTRODUCTION_REQUEST);

	private static final long serialVersionUID = 1L;

	public IntroductionDeniedMessage(IntroductionRequest req) {
		super(req.requester, req.introducer, req, MessageType.INTRODUCTION_DENIED);
	}

}
