package com.github.glfrazier.snd.util;

import java.io.IOException;
import java.net.InetAddress;

public interface VPNManager {

	/**
	 * Create a VPN.
	 * 
	 * @param neighbor       the node to which this node is being connected.
	 * @param keyingMaterial crypto material needed to construct the VPN.
	 * @throws IOException the VPN cannot be created.
	 */
	public void createVPN(InetAddress neighbor, Object keyingMaterial) throws IOException;

	public void closeVPN(InetAddress remote);

}
