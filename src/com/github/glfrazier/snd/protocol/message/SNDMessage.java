package com.github.glfrazier.snd.protocol.message;

import java.net.InetAddress;

import com.github.glfrazier.snd.protocol.IntroductionRequest;

/**
 * The messages that comprise the Social Network Dynamics Protocol (SNDP).
 *
 */
public class SNDMessage extends Message {

	private static final long serialVersionUID = 1L;

	protected MessageType type;

	public enum MessageType {
		/**
		 * A message from the requester to the introducer of the introduction,
		 * requesting to be introduced
		 */
		INTRODUCTION_REQUEST,

		/**
		 * A message from the introducer to the target of the introduction, offering the
		 * introduction
		 */
		INTRODUCTION_OFFER,

		/**
		 * A message from the target of an introduction to the introducer refusing the
		 * introduction
		 */
		INTRODUCTION_REFUSED,

		/**
		 * A message from the target to the introducer, accepting the introduction
		 */
		INTRODUCTION_ACCEPTED,

		/**
		 * A message from the introducer to the requester, completing the accepted
		 * introduction
		 */
		INTRODUCTION_COMPLETED,

		/**
		 * A message from the introducer to the requester, denying the introduction
		 * either because the target of the introduction refused the offer or because
		 * the introducer refused to make the offer
		 */
		INTRODUCTION_DENIED,

		/**
		 * A message from an introducer to the requester, denying the introduction BUT
		 * announcing the ability to route to the destination. This will typically be
		 * sent by a server proxy (because the proxied-for app server does not
		 * participate in the SND protocol), but technically this could occur at any
		 * point in the topology.
		 */
		INTRODUCTION_DENIED_WILL_ROUTE,

		/**
		 * Send/receive feedback about a transaction
		 */
		FEEDBACK
	};

	private final IntroductionRequest req;

	public SNDMessage(InetAddress dst, InetAddress src, IntroductionRequest req, MessageType type) {
		super(dst, src);
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

//	public static class IntroSequenceID implements Serializable {
//
//		private static final long serialVersionUID = 1L;
//		private final InetAddress dst;
//		private final InetAddress src;
//		private final long l;
//
//		public IntroSequenceID(InetAddress dst, InetAddress src) {
//			l = INDEX.getAndIncrement();
//			this.dst = dst;
//			this.src = src;
//		}
//
//		@Override
//		public int hashCode() {
//			return Long.hashCode(l) + dst.hashCode() + src.hashCode();
//		}
//
//		@Override
//		public boolean equals(Object o) {
//			if (!(o instanceof IntroSequenceID)) {
//				return false;
//			}
//			IntroSequenceID uid = (IntroSequenceID)o;
//			return l == uid.l && dst.equals(uid.dst) && src.equals(uid.src);
//		}
//
//		@Override
//		public String toString() {
//			return "UUID:" + dst + "<==" + src + "(" + l + ")";
//		}
//	}

}
