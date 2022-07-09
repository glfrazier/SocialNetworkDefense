package com.github.glfrazier.snd.protocol.message;

import java.net.InetAddress;

import com.github.glfrazier.snd.protocol.IntroductionRequest;

public class RemoveIntroductionRequestMessage extends IntroductionMessage {

	private static final long serialVersionUID = 1L;


	public RemoveIntroductionRequestMessage(InetAddress dst, InetAddress src, IntroductionRequest req) {
		super(dst, src, req, MessageType.REMOVE_INTRODUCTION);
	}

}