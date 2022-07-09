package com.github.glfrazier.snd.protocol.message;
import static com.github.glfrazier.snd.protocol.IntroductionRequest.SAMPLE_INTRODUCTION_REQUEST;
import java.io.Serializable;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.protocol.IntroductionRequest;

public class IntroductionRefusedMessage extends IntroductionMessage implements Serializable, Event {

	/**
	 * 
	 */
	public static final IntroductionRefusedMessage INTRODUCTION_REFUSED = new IntroductionRefusedMessage(SAMPLE_INTRODUCTION_REQUEST, null);

	private static final long serialVersionUID = 1L;

	public IntroductionRefusedMessage(IntroductionRequest req, InetAddress target) {
		// refused is sent from target to introducer
		super(req.introducer, // dst
				target, // src
				req, MessageType.INTRODUCTION_REFUSED);
	}

}
