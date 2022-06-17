package com.github.glfrazier.snd.protocol.message;

import static com.github.glfrazier.snd.protocol.IntroductionRequest.SAMPLE_INTRODUCTION_REQUEST;

import java.io.Serializable;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.node.Feedback;
import com.github.glfrazier.snd.protocol.IntroductionRequest;

/**
 * This message is sent from the target of an introduction that was previously
 * accepted to the introducer, providing feedback regarding the transaction.
 *
 */
public class FeedbackMessage extends SNDMessage implements Serializable, Event {

	/**
	 * 
	 */
	public static final FeedbackMessage SAMPLE_FEEDBACK_MESSAGE = //
			new FeedbackMessage(SAMPLE_INTRODUCTION_REQUEST, null, null, null);

	private static final long serialVersionUID = 1L;

	private Feedback feedback;

	private InetAddress subject;

	/**
	 * Construct Feedback for the (bad) behavior of the subject IP address.
	 * 
	 * @param req      The IntroductionRequest by which the sender became connected
	 *                 to the subject.
	 * @param sender   The node sending the feedback.
	 * @param subject  The node that the feedback is about.
	 * @param feedback The (bad) feedback.
	 */
	public FeedbackMessage(IntroductionRequest req, InetAddress sender, InetAddress subject, Feedback feedback) {
		super(req.introducer, sender, req, MessageType.FEEDBACK);
		this.subject = subject;
		this.feedback = feedback;
	}

	public InetAddress getSubject() {
		return subject;
	}

	public Feedback getFeedback() {
		return feedback;
	}
	
	@Override
	public String toString() {
		String result = super.toString();
		return result + " regarding " + getSubject();
	}

}
