package com.github.glfrazier.snd.protocol.message;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;

import java.io.Serializable;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.protocol.IntroductionRequest;

public class IntroductionCompletedMessage extends IntroductionMessage implements Serializable, Event {

	private static final long serialVersionUID = 1L;
	private final InetAddress nextStep;
	private final Object keyingMaterial;

	public IntroductionCompletedMessage(IntroductionRequest req, Object keyingMaterial, InetAddress target) {
		super(req.requester, req.introducer, req, MessageType.INTRODUCTION_COMPLETED);
		this.keyingMaterial = keyingMaterial;
		this.nextStep = target;
	}

	public InetAddress getNewNeighbor() {
		return nextStep;
	}

	public Object getKeyingMaterial() {
		return keyingMaterial;
	}

	public String toString() {
		return super.toString() + "(connected to " + addrToString(nextStep) + ")";
	}

}
