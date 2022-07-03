package com.github.glfrazier.snd.node;

import java.net.InetAddress;
import java.util.logging.Logger;

import com.github.glfrazier.snd.protocol.message.Message;
import com.github.glfrazier.snd.util.VPN;

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

	/**
	 * Notify the message receiver that a VPN has been closed.
	 */
	public void vpnClosed(VPN vpn);

	/**
	 * Used by (e.g.,) {@link VPN} implementations to log activity via their owning entity's logger.
	 * 
	 * @return
	 */
	public Logger getLogger();

}
