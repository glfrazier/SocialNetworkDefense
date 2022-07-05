package com.github.glfrazier.snd.protocol.message;

import com.github.glfrazier.snd.simulation.SimVPNImpl;

/**
 * An AcknowledgeMessage is a message that has a VPN sequence number and
 * contains an acknowledgement for messages received. SND Messages are
 * Acknowledgement Messages. This interface was kept separate (not incorporated
 * directly into SNDMessage) so that VPN implementations could piggyback on the
 * acknowledgement scheme.
 * 
 * Given that there is code that casts AcknowledgeMessage objects to Message,
 * one should only use this interface in Message subclass implementations.
 * 
 */
public interface AcknowledgeMessage {

	/**
	 * Get the sequence number of this message. See the implementations of
	 * {@link SimVPNImpl#send(Message)} and {@link SimVPNImpl#receive(Message)}
	 * 
	 * @return the message's sequence number
	 */
	public int getSequenceNumber();

	/**
	 * Set the sequence number of this message. See the implementations of
	 * {@link SimVPNImpl#send(Message)} and {@link SimVPNImpl#receive(Message)}
	 */
	public void setSequenceNumber(int sequenceNumber);

	/**
	 * If <code>this.containsAcknowledgements() == true</code>, then this method
	 * returns the last contiguous sequence number&mdash;the sequence number of the
	 * last message for which there were no previously-dropped messages. If
	 * <code>this.containsAcknowledgements() == false</code>, this method will
	 * return a meaningless value.
	 * 
	 * @return the last contiguous sequence number
	 */
	public int getLastContiguousSequenceNumberReceived();

	/**
	 * Specify the sequence number of the last message received such that no
	 * previous messages were lost. Note that setting the last contiguous sequence
	 * number also causes <code>this.containsAcknowledgements()</code> to return
	 * true.
	 * 
	 * @param lastContiguousSequenceNumberReceived the last contiguous sequence
	 *                                             number that has been received
	 */
	public void setLastContiguousSequenceNumberReceived(int lastContiguousSequenceNumberReceived);

	/**
	 * 
	 * @return true if this message identifies the last contiguous sequence number
	 *         received.
	 */
	public boolean containsAcknowledgements();
}
