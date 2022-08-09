package com.github.glfrazier.snd.protocol.message;

/**
 * Acknowledge receipt of SND messages.
 * 
 */
public class AckMessage extends SNDPMessage {

	private static final long serialVersionUID = 1L;
	
	private IntroductionMessage m;
	
	public AckMessage(IntroductionMessage msg) {
		super(msg.getSrc(), msg.getDst(), msg.getIdentifier(), MessageType.ACK);
		this.m = msg;
	}
	
	public String toString() {
		return super.toString() + " ack'ing " + m;
	}
}
