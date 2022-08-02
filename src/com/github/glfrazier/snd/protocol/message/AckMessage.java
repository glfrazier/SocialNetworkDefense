package com.github.glfrazier.snd.protocol.message;

/**
 * Acknowledge receipt of SND messages.
 * 
 */
public class AckMessage extends SNDPMessage {

	private static final long serialVersionUID = 1L;
	
	public AckMessage(IntroductionMessage msg) {
		super(msg.getSrc(), msg.getDst(), msg.getIdentifier(), MessageType.ACK);
	}
	
	
}
