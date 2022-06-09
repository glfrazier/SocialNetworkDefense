package com.github.glfrazier.snd.protocol.message;

import static com.github.glfrazier.snd.protocol.IntroductionRequest.SAMPLE_INTRODUCTION_REQUEST;

import java.io.Serializable;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.Pedigree;

/**
 * 
 */
public class IntroductionOfferMessage extends SNDMessage implements Serializable, Event {

	/**
	 * 
	 */
	public static final IntroductionOfferMessage INTRODUCTION_OFFER = new IntroductionOfferMessage(
			SAMPLE_INTRODUCTION_REQUEST, null, null);

	private static final long serialVersionUID = 1L;
	private Pedigree pedigree;

	public IntroductionOfferMessage(IntroductionRequest req, InetAddress target, Pedigree pedigree) {
		super(target, req.introducer, req, MessageType.INTRODUCTION_OFFER);
		this.pedigree = pedigree;
	}

	public Pedigree getPedigree() {
		return pedigree;
	}

}
