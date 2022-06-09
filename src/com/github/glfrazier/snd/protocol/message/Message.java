package com.github.glfrazier.snd.protocol.message;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import com.github.glfrazier.event.Event;

public abstract class Message implements Serializable, Event {

	private static final long serialVersionUID = 1L;
	private final InetAddress dst;
	private final InetAddress src;

	protected MessageType type;

	public enum MessageType {
		/** A message from a client to a server */
		CLIENT_TO_SERVER,

		/**
		 * The message from the server to client in response to the CLIENT_TO_SERVER
		 * message.
		 */
		SERVER_TO_CLIENT,

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

	public Message(InetAddress dst, InetAddress src, MessageType type) {
		this.dst = dst;
		this.src = src;
		this.type = type;
	}

	public InetAddress getDst() {
		return dst;
	}

	public InetAddress getSrc() {
		return src;
	}

	public MessageType getType() {
		return type;
	}

	public String toString() {
		return type + "(" + dst + "<==" + src + ")";
	}
}
