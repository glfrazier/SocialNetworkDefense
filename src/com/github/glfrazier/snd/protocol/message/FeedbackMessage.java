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
	 * Construct Feedback for the (bad) behavior of the subject IP address. This is
	 * the constructor used in the case where the reporting node was introduced to
	 * the subject and is sending the feedback to the introducer.
	 * 
	 * @param req      The IntroductionRequest by which the sender became connected
	 *                 to the subject.
	 * @param sender   The node sending the feedback.
	 * @param subject  The node that the feedback is about.
	 * @param feedback The (bad) feedback.
	 */
	public FeedbackMessage(IntroductionRequest req, InetAddress sender, InetAddress subject, Feedback feedback,
			Message trigger) {
		super(req.introducer, sender, req, MessageType.FEEDBACK);
		this.subject = subject;
		this.feedback = feedback;
		this.trigger = trigger;
	}

	/**
	 * The constructor to be used in special cases. Examples:
	 * <ul>
	 * <li>A host that is not a direct participant in the SND network, but rather
	 * has a proxy, uses this constructor to send feedback to the proxy regarding
	 * observed misbehavior. The <code>subject</code> parameter is the IP address
	 * observed to misbehave.</li>
	 * <li>The first introducer in the introduction chain does not have an
	 * IntroductionRequest by which it knows the requester. So, instead of using the
	 * constructor that takes an IntroductionRequest, it uses this constructor.</li>
	 * </ul>
	 * 
	 * @param dst      The host to receive this feedback. that originated the
	 *                 transaction&mdash;the node that requested the
	 *                 introduction(s).
	 * @param src      The host sending the feedback (presumably the host on which
	 *                 this message is being constructed).
	 * @param subject  The host that the feedback is about.
	 * @param feedback The feedback, which for now is always bad.
	 */
	public FeedbackMessage(InetAddress dst, InetAddress src, InetAddress subject, Feedback feedback, Message trigger) {
		super(dst, src, null, MessageType.FEEDBACK);
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
