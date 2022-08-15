package com.github.glfrazier.snd.node;

import java.net.InetAddress;

import com.github.glfrazier.event.EventProcessor;
import com.github.glfrazier.snd.protocol.message.Message;

/**
 * This interface facilitates building the simulation, by allowing there to be
 * entities other than {@link Node} instances that can generate or receive
 * traffic over VPNs.
 *
 */
public interface MessageReceiver extends EventProcessor {

	/**
	 * Obtain the address of this entity.
	 * 
	 * @return the address
	 */
	public InetAddress getAddress();
	
	/**
	 * Notification of VPN closure.
	 * 
	 * @param nbr address of the neighbor to which the VPN has closed.
	 */
	public void vpnClosed(InetAddress nbr);
	
}
