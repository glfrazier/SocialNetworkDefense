package com.github.glfrazier.snd.util;

import java.io.IOException;
import java.net.InetAddress;

import com.github.glfrazier.snd.node.MessageReceiver;

public interface VPNFactory {

	/**
	 * Create a long-lived VPN.
	 * 
	 * @param link         the link with which this VPN is associated.
	 * @param keyingMaterial crypto material needed to construct the VPN.
	 * @return the created VPN.
	 * @throws IOException the VPN cannot be created.
	 */
	public VPN createVPN(MessageReceiver local, InetAddress remote, Object keyingMaterial) throws IOException;

}
