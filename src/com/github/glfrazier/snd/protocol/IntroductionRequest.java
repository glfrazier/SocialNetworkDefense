package com.github.glfrazier.snd.protocol;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicLong;

import com.github.glfrazier.snd.util.AddressUtils;

public class IntroductionRequest implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final AtomicLong NONCE_GENERATOR = new AtomicLong(0);

	public static final IntroductionRequest SAMPLE_INTRODUCTION_REQUEST = new IntroductionRequest(
			AddressUtils.ZERO_IPv4_ADDRESS, AddressUtils.ZERO_IPv4_ADDRESS, AddressUtils.ZERO_IPv4_ADDRESS);

	public final InetAddress requester;
	public final InetAddress introducer;
	public final InetAddress destination;
	private final long nonce;
	private transient String stringValue;

	/**
	 * Construct an introduction request using the addresses of the requester (the
	 * node making the request), the introducer (the requester is asking the
	 * introducer for the introduction) and the target (the node to which the
	 * requester wishes to be connected).
	 * 
	 * @param requester the requesting node
	 * @param introducer the node of whom the introduction is being requested
	 * @param destination the node to which the requester wishes to be connected
	 */
	public IntroductionRequest(InetAddress requester, InetAddress introducer, InetAddress destination) {
		this.requester = requester;
		this.introducer = introducer;
		this.destination = destination;
		this.nonce = NONCE_GENERATOR.getAndIncrement();
	}

	public int hashCode() {
		return requester.hashCode() ^ introducer.hashCode() ^ destination.hashCode() ^ Long.hashCode(nonce);
	}

	public boolean equals(Object o) {
		if (!(o instanceof IntroductionRequest)) {
			return false;
		}
		IntroductionRequest ir = (IntroductionRequest) o;
		return requester.equals(ir.requester) && introducer.equals(ir.introducer) && destination.equals(ir.destination)
				&& nonce == ir.nonce;
	}

	public String toString() {
		if (stringValue == null) {
			stringValue = "IntroductionRequest(" + addrToString(requester) + ", " + addrToString(introducer) + ", "
					+ addrToString(destination) + ", " + nonce + ")";
		}
		return stringValue;
	}
}
