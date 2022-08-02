package com.github.glfrazier.snd.protocol.message;

import java.io.Serializable;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.protocol.IntroductionRequest;

public class IntroductionDeniedMessage extends IntroductionMessage implements Serializable, Event {

	private static final long serialVersionUID = 1L;

	public IntroductionDeniedMessage(IntroductionRequest req) {
		super(req.requester, req.introducer, req, MessageType.INTRODUCTION_DENIED);
	}

}
