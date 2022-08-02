package com.github.glfrazier.snd.node;

import java.net.InetAddress;

import com.github.glfrazier.snd.protocol.message.Message;

/**
 * This interface facilitates building the simulation, by allowing there to be
 * entities other than {@link SNDNode} instances that can generate or receive
 * traffic over VPNs.
 *
 */
public interface MessageReceiver {

	/**
	 * Receive a message.
	 * 
	 * @param m The message being received.
	 */
	public void receive(Message m);

	/**
	 * Obtain the address of this entity.
	 * 
	 * @return the address
	 */
	public InetAddress getAddress();
	
}
