package com.github.glfrazier.snd.protocol.message;

import java.net.InetAddress;

public class WrappedMessage extends Message {

	private static final long serialVersionUID = 1L;

	public WrappedMessage(InetAddress dst, InetAddress src, Message msg) {
		super(dst, src, msg);
	}

	public Message getEnclosedMessage() {
		return (Message) super.getContent();
	}

	public String toString() {
		return toString("Wrapper");
	}

}
