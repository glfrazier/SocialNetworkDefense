package com.github.glfrazier.snd.protocol.message;

import java.net.InetAddress;

import com.github.glfrazier.snd.protocol.IntroductionRequest;

/**
 * The messages that comprise an introduction handshake.
 *
 */
public abstract class IntroductionMessage extends SNDPMessage implements AcknowledgeMessage {

	private static final long serialVersionUID = 1L;

	private final IntroductionRequest req;
	
	private int sequenceNumber;
	
	private int lastContiguousSequenceNumberReceived;
	
	private boolean acksSet;

	@Override
	public int getSequenceNumber() {
		return sequenceNumber;
	}

	@Override
	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	@Override
	public int getLastContiguousSequenceNumberReceived() {
		return lastContiguousSequenceNumberReceived;
	}

	@Override
	public void setLastContiguousSequenceNumberReceived(int lastContiguousSequenceNumberReceived) {
		this.acksSet = true;
		this.lastContiguousSequenceNumberReceived = lastContiguousSequenceNumberReceived;
	}

	@Override
	public boolean containsAcknowledgements() {
		return acksSet;
	}

	public IntroductionMessage(InetAddress dst, InetAddress src, IntroductionRequest req, MessageType type) {
		super(dst, src, type);
		this.req = req;
		this.type = type;
	}

	public MessageType getType() {
		return type;
	}
	
	public IntroductionRequest getIntroductionRequest() {
		return req;
	}

	@Override
	public String toString() {
		return super.toString(type.toString()) + "(" + req + ")";
	}

}
