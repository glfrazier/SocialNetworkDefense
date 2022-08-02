package com.github.glfrazier.snd.protocol.message;

import java.net.InetAddress;

import com.github.glfrazier.snd.protocol.IntroductionRequest;

/**
 * The messages that comprise an introduction handshake.
 *
 */
public abstract class IntroductionMessage extends SNDPMessage {

	private static final long serialVersionUID = 1L;

	private final IntroductionRequest req;

	public IntroductionMessage(InetAddress dst, InetAddress src, IntroductionRequest req, MessageType type) {
		super(dst, src, type);
		this.req = req;
	}
	
	public IntroductionRequest getIntroductionRequest() {
		return req;
	}

	@Override
	public String toString() {
		return super.toString(type.toString()) + "(" + req + ")";
	}

}
