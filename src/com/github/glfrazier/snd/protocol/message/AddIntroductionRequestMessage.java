package com.github.glfrazier.snd.protocol.message;

import java.net.InetAddress;

import com.github.glfrazier.snd.protocol.IntroductionRequest;

public class AddIntroductionRequestMessage extends IntroductionMessage {

	private static final long serialVersionUID = 1L;

	public AddIntroductionRequestMessage(InetAddress dst, InetAddress src, IntroductionRequest req) {
		super(dst, src, req, MessageType.ADD_INTRODUCTION_REQUEST);
	}

}