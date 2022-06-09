package com.github.glfrazier.snd.util;

import java.net.InetAddress;

import com.github.glfrazier.snd.protocol.message.Message;

/**
 * An entity that possesses VPNs must provide an interface for receiving data
 * from them. When one initializes a VPNFactory, one is identifying the listener
 * for the VPNs that the factory creates.
 * 
 * @see VPNFactory#initialize(VPNListener)
 *
 */
public interface VPNEndpoint {

	/**
	 * The address by which the listener is addressed. This is the address for the VPN endpoint.
	 * @return the address of the listener
	 */
	public InetAddress getAddress();
	
	/**
	 * Receive a message from the VPN.
	 * 
	 * @param m
	 * @param vpn
	 */
	public void receive(Message m, VPN vpn);
	
}
