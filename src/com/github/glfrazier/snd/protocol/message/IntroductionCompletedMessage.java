package com.github.glfrazier.snd.protocol.message;

import static com.github.glfrazier.snd.protocol.IntroductionRequest.SAMPLE_INTRODUCTION_REQUEST;
import static com.github.glfrazier.snd.util.AddressUtils.addrToString;

import java.io.Serializable;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.protocol.IntroductionRequest;

public class IntroductionCompletedMessage extends SNDMessage implements Serializable, Event {

	public static final IntroductionCompletedMessage SAMPLE_INTRODUCTION_SUCCESS = //
			new IntroductionCompletedMessage(SAMPLE_INTRODUCTION_REQUEST, null);

	private static final long serialVersionUID = 1L;
	private InetAddress nextStep;

	public IntroductionCompletedMessage(IntroductionRequest req, InetAddress target) {
		super(req.requester, req.introducer, req, MessageType.INTRODUCTION_COMPLETED);
		this.nextStep = target;
	}

	public InetAddress getNewNeighbor() {
		return nextStep;
	}

	public Object getKeyingMaterial() {
		return null;
	}

	public String toString() {
		return super.toString() + "(connected to " + addrToString(nextStep) + ")";
	}

}
