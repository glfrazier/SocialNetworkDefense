package com.github.glfrazier.snd.protocol;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicLong;

import com.github.glfrazier.snd.util.AddressUtils;

public class IntroductionRequest implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final AtomicLong INDEX = new AtomicLong(0);

	public static final IntroductionRequest SAMPLE_INTRODUCTION_REQUEST = new IntroductionRequest(
			AddressUtils.ZERO_IPv4_ADDRESS, AddressUtils.ZERO_IPv4_ADDRESS, AddressUtils.ZERO_IPv4_ADDRESS);
	
	public final InetAddress requester;
	public final InetAddress introducer;
	public final InetAddress destination;
	private final long nonce;
	private transient String stringValue;

	public IntroductionRequest(InetAddress r, InetAddress i, InetAddress t) {
		requester = r;
		introducer = i;
		destination = t;
		nonce = INDEX.getAndIncrement();
	}

	public int hashCode() {
		return requester.hashCode() ^ introducer.hashCode() ^ destination.hashCode() ^ Long.hashCode(nonce);
	}

	public boolean equals(Object o) {
		if (!(o instanceof IntroductionRequest)) {
			return false;
		}
		IntroductionRequest intro = (IntroductionRequest) o;
		return requester.equals(intro.requester) && introducer.equals(intro.introducer)
				&& destination.equals(intro.destination) && nonce == intro.nonce;
	}

	public String toString() {
		if (stringValue == null) {
			stringValue = "IntroductionRequest(" + requester + ", " + introducer + ", " + destination + ", " + nonce + ")";
		}
		return stringValue;
	}
}
