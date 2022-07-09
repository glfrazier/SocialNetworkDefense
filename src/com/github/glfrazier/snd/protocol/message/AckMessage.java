package com.github.glfrazier.snd.protocol.message;

import java.net.InetAddress;

/**
 * Acknowledge receipt of SND messages.
 * 
 */
public class AckMessage extends SNDPMessage {

	private static final long serialVersionUID = 1L;
	private int lastContiguousSequenceNumberReceived;
	private boolean containsAcks;
	
	public AckMessage(InetAddress dst, InetAddress src) {
		super(dst, src, MessageType.ACK);
	}
	
	/**
	 * If <code>this.containsAcknowledgements() == true</code>, then this method
	 * returns the last contiguous sequence number&mdash;the sequence number of the
	 * last message for which there were no previously-dropped messages. If
	 * <code>this.containsAcknowledgements() == false</code>, this method will
	 * return a meaningless value.
	 * 
	 * @return the last contiguous sequence number
	 */
	public int getLastContiguousSequenceNumberReceived() {
		return lastContiguousSequenceNumberReceived;
	}

	/**
	 * Specify the sequence number of the last message received such that no
	 * previous messages were lost. Note that setting the last contiguous sequence
	 * number also causes <code>this.containsAcknowledgements()</code> to return
	 * true.
	 * 
	 * @param lastContiguousSequenceNumberReceived the last contiguous sequence
	 *                                             number that has been received
	 */
	public void setLastContiguousSequenceNumberReceived(int lastContiguousSequenceNumberReceived) {
		this.lastContiguousSequenceNumberReceived = lastContiguousSequenceNumberReceived;
		this.containsAcks = true;
	}

	/**
	 * 
	 * @return true if this message identifies the last contiguous sequence number
	 *         received.
	 */
	public boolean containsAcknowledgements() {
		return containsAcks;
	}
	
}
