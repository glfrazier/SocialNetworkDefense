package com.github.glfrazier.snd.protocol.message;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;

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
public class FeedbackMessage extends IntroductionMessage implements Serializable, Event {

	private static final long serialVersionUID = 1L;

	private Feedback feedback;

	private InetAddress subject;

	private final Message trigger;

	/**
	 * Construct Feedback for the (bad) behavior of the subject IP address.
	 * 
	 * @param req      The IntroductionRequest by which the sender became connected
	 *                 to the subject.
	 * @param sender   The node sending the feedback.
	 * @param subject  The node that the feedback is about.
	 * @param feedback The (bad) feedback.
	 */
	public FeedbackMessage(IntroductionRequest req, InetAddress sender, InetAddress subject, Feedback feedback, Message trigger) {
		super(req.introducer, sender, req, MessageType.FEEDBACK);
		this.subject = subject;
		this.feedback = feedback;
		this.trigger = trigger;
	}

	/**
	 * Construct Feedback for the (bad) behavior of the subject IP address. This
	 * constructor is to be used by nodes that are not SNDP participants, e.g., the
	 * servers residing behind ServerProxies.
	 * 
	 * @param sender   The node sending the feedback.
	 * @param subject  The node that the feedback is about.
	 * @param feedback The (bad) feedback.
	 */
	public FeedbackMessage(InetAddress sender, InetAddress subject, Feedback feedback, Message trigger) {
		super(subject, sender, null, MessageType.FEEDBACK);
		this.subject = subject;
		this.feedback = feedback;
		this.trigger = trigger;
	}

	/**
	 * The constructor to be used by the last introducer in the pedigree, sending
	 * feedback to the client.
	 * 
	 * @param requester  The node that originated the transaction&mdash;the node
	 *                   that requested the introduction(s).
	 * @param introducer The of whom the introduction was requested and is providing
	 *                   the feedback.
	 * @param subject    The node that the feedback is about. Will be the requester,
	 *                   unless the requester is proxying for other clients.
	 * @param feedback   The feedback, which for now is always bad.
	 */
	public FeedbackMessage(InetAddress requester, InetAddress introducer, InetAddress subject, Feedback feedback, Message trigger) {
		super(requester, introducer, null, MessageType.FEEDBACK);
		this.subject = subject;
		this.feedback = feedback;
		this.trigger = trigger;
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
		return result + " regarding " + addrToString(getSubject()) + ", triggered by " + trigger;
	}

	public Message getTrigger() {
		return trigger;
	}

}
