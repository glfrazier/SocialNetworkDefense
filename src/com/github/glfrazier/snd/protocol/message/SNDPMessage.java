package com.github.glfrazier.snd.protocol.message;

import java.net.InetAddress;

import com.github.glfrazier.snd.protocol.IntroductionRequest;

/**
 * The messages that comprise an introduction handshake.
 *
 */
public abstract class SNDPMessage extends Message {

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
		FEEDBACK,

		/**
		 * Tell the other end of an introduced VPN to remove an introduction to the VPN
		 */
		REMOVE_INTRODUCTION,

		/**
		 * Tell the other end of an introduced VPN to add an introduction to the VPN
		 */
		ADD_INTRODUCTION,

		/**
		 * Acknowledge receipt of messages.
		 */
		ACK,

		/**
		 * Let the opposing node know that the link has been half-closed. This has the
		 * semantics of an ACK, except that it must itself be acknowledged.
		 */
		LINK_HALF_CLOSED
	};

	public SNDPMessage(InetAddress dst, InetAddress src, MessageType type) {
		super(dst, src);
		this.type = type;
	}

	public MessageType getType() {
		return type;
	}

	@Override
	public String toString() {
		return super.toString(type.toString());
	}

}
