package com.github.glfrazier.snd.util;

import java.io.IOException;
import java.net.InetAddress;

import com.github.glfrazier.snd.protocol.IntroductionRequest;

public interface VPNFactory {

	/**
	 * Create a long-lived VPN.
	 * 
	 * @param remote         the address to which this host is to be connected.
	 * @param keyingMaterial crypto material needed to construct the VPN.
	 * @return the created VPN.
	 * @throws IOException the VPN cannot be created.
	 */
	public VPN createVPN(InetAddress remote, Object keyingMaterial) throws IOException;

	public VPN createIntroducedVPN(InetAddress remote, IntroductionRequest transaction, Object keyingMaterial) throws IOException;

}
