package com.github.glfrazier.snd.protocol.message;

import java.io.Serializable;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.protocol.IntroductionRequest;

public class IntroductionAcceptedMessage extends IntroductionMessage implements Serializable, Event {

	private static final long serialVersionUID = 1L;
	
	protected final Object keyingMaterial;

	public IntroductionAcceptedMessage(IntroductionRequest req, Object keyingMaterial, InetAddress target) {
		super(req.introducer, target, req, MessageType.INTRODUCTION_ACCEPTED);
		this.keyingMaterial = keyingMaterial;
	}
	
	public Object getKeyingMaterial() {
		return keyingMaterial;
	}

}
