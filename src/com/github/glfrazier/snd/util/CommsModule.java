package com.github.glfrazier.snd.util;

import java.io.IOException;
import java.net.InetAddress;

import com.github.glfrazier.snd.protocol.message.Message;

public interface CommsModule {

	/**
	 * Add a route to the routing table. The destination is reachable by
	 * sending/forwarding packets to the route.
	 * 
	 * @param dst   the packet destination
	 * @param route the forwarding host
	 */
	public void addRoute(InetAddress dst, InetAddress route);

	/**
	 * Remove a route from the routing table.
	 * 
	 * @param dst   the packet destination
	 * @param route the (no-longer) forwarding host
	 * @return true if there was a route to remove, false if not
	 */
	public boolean removeRoute(InetAddress dst, InetAddress route);

	/**
	 * Remove all entries from the routing table that specify <code>forwarder</code>
	 * as the forwarder.
	 * 
	 * @param forwarder
	 */
	public void removeRoutesVia(InetAddress forwarder);

	/**
	 * Discover whether a given destination can be reached.
	 * 
	 * @param dst
	 * @return true if there is a route to the destination, false otherwise.
	 */
	public boolean canSendTo(InetAddress dst);

	/**
	 * Send a message (packet).
	 * 
	 * @param msg
	 * @throws IOException if there is no route to the destination or some other
	 *                     detectable transmission failure.
	 */
	public void send(Message msg) throws IOException;

}
