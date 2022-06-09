package com.github.glfrazier.snd.protocol.message;

import static com.github.glfrazier.snd.protocol.IntroductionRequest.SAMPLE_INTRODUCTION_REQUEST;
import java.io.Serializable;
import java.net.InetAddress;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.snd.protocol.Feedback;
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
			new FeedbackMessage(SAMPLE_INTRODUCTION_REQUEST, null, null);

	private static final long serialVersionUID = 1L;

	private Feedback feedback;

	public FeedbackMessage(IntroductionRequest req, InetAddress target, Feedback feedback) {
		super(req.introducer, target, req, MessageType.FEEDBACK);
		this.feedback = feedback;
	}

	public Feedback getFeedback() {
		return feedback;
	}

}
