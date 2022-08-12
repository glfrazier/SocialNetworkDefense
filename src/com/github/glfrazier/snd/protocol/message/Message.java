package com.github.glfrazier.snd.protocol.message;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import com.github.glfrazier.event.Event;

public class Message implements Serializable, Event {

	private static final long serialVersionUID = 1L;
	private final InetAddress dst;
	private final InetAddress src;
	private final Serializable content;
	
	private final boolean verbose;

	public Message(InetAddress dst, InetAddress src) {
		this.dst = dst;
		this.src = src;
		this.content = null;
		this.verbose = false;
	}

	public Message(InetAddress dst, InetAddress src, Serializable content) {
		this.dst = dst;
		this.src = src;
		this.content = content;
		this.verbose = false;
	}

	public Message(InetAddress dst, InetAddress src, Serializable content, boolean verbose) {
		this.dst = dst;
		this.src = src;
		this.content = content;
		this.verbose = verbose;
	}

	public InetAddress getDst() {
		return dst;
	}

	public InetAddress getSrc() {
		return src;
	}

	public Serializable getContent() {
		return content;
	}
	
	public boolean isVerbose() {
		return verbose;
	}
	
	public String toString() {
		return toString(getClass().getSimpleName());
	}

	protected String toString(String t) {
		return t + "(" + addrToString(dst) + "<==" + addrToString(src)
				+ (content == null ? ")" : " '" + content.toString() + "')");
	}
}
