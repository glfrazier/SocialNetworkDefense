package com.github.glfrazier.snd.protocol.message;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The messages that comprise an introduction handshake.
 *
 */
public abstract class SNDPMessage extends Message {

	private static final long serialVersionUID = 1L;
	
	private static final AtomicInteger INDEX = new AtomicInteger(0);

	protected final long id;
	
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
		ADD_INTRODUCTION_REQUEST,

		/**
		 * Acknowledge receipt of messages.
		 */
		ACK
	};

	public SNDPMessage(InetAddress dst, InetAddress src, MessageType type) {
		super(dst, src);
		this.id = ((long)src.hashCode() << 32) + INDEX.getAndIncrement();
		this.type = type;
	}
	
	protected SNDPMessage(InetAddress dst, InetAddress src, long id, MessageType type) {
		super(dst, src);
		this.id = id;
		this.type = type;
	}

	public MessageType getType() {
		return type;
	}

	@Override
	public String toString() {
		return super.toString(type.toString() + "." + id);
	}

	public final long getIdentifier() {
		return id;
	}

}
