package com.github.glfrazier.snd.util;

import java.net.InetAddress;

import com.github.glfrazier.snd.protocol.IntroductionRequest;

public interface VPNFactory {

	/**
	 * The VPN factory must be initialized before it can be used.
	 * 
	 * @param owner
	 */
	public void initialize(VPNEndpoint owner);

	/**
	 * Create a long-lived VPN.
	 * 
	 * @param remote the address to which this host is to be connected.
	 * @return the created VPN.
	 * @throws IllegalStateException if {@link #initialize} has not been called.
	 */
	public VPN createVPN(InetAddress remote // , Crypto keys TODO add keying material parameter
	) throws IllegalStateException;

	public VPN createEphemeralVPN(InetAddress remote, IntroductionRequest transaction // , Crypto keys
	);

}
