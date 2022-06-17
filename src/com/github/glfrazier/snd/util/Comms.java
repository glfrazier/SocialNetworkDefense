package com.github.glfrazier.snd.util;

import java.io.IOException;
import java.net.InetAddress;

import com.github.glfrazier.snd.node.MessageReceiver;
import com.github.glfrazier.snd.node.SNDNode;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.message.Message;

public interface Comms {

	/**
	 * Add a route to the routing table.
	 * 
	 * @param dst    The destination for which there is a route.
	 * @param router The intermediary node that routes to the destination.
	 */
	public void addRoute(InetAddress dst, InetAddress router);

	/**
	 * Remove a route from the routing table.
	 * 
	 * @param dst    The destination for which there is a route.
	 * @param router The intermediary node that routes to the destination.
	 * @return <code>true</code> if the specified route was removed.
	 */
	public boolean removeRoute(InetAddress dst, InetAddress router);

	/**
	 * Send a message.
	 * 
	 * @param msg The message to send.
	 * @throws IOException If the transmission fails (e.g., there is no route to the
	 *                     message's destination).
	 */
	public void send(Message msg) throws IOException;

	public void openVPN(InetAddress nbr, Object keyingMaterial) throws IOException;

	public void openIntroducedVPN(InetAddress nbr, IntroductionRequest request, Object keyingMaterial)
			throws IOException;

	/**
	 * Close the VPN to the neighbor, regardless of whether it is long-lived or
	 * introduced.
	 * 
	 * @param nbr
	 * @return
	 * @throws IOException
	 */
	public boolean closeVPN(InetAddress nbr) throws IOException;

	/**
	 * Close an introduced VPN to <code>nbr</code>. If the VPN is long-lived, this
	 * method has no effect.
	 * 
	 * @param nbr
	 * @return
	 * @throws IOException
	 */
	public boolean closeIntroducedVPN(InetAddress nbr) throws IOException;

	/**
	 * @param dst
	 * @return <code>true</code> if there is a VPN or route to the destination.
	 */
	public boolean canSendTo(InetAddress dst);

	/**
	 * Notify the Comms package that a VPN has been closed. The SNDNode invokes this
	 * in response to the {@link MessageReceiver#vpnClosed(VPN)} invocation. If you
	 * are coding a VPN implementation, when a VPN is closed, it should invoke
	 * {@link SNDNode#vpnClosed(VPN)}, <emph>NOT</emph> this method. This maximizes
	 * the separation (modularity) between the VPN implementation and the Comms
	 * implementation, and allows one to inject behavior monitoring code in the
	 * SNDNode.
	 * 
	 * @param vpn
	 */
	public void vpnClosed(VPN vpn);

	/**
	 * Obtain the IntroductionRequest that resulted in there being a VPN to the
	 * specified neighbor.
	 * 
	 * @param nbr The node to which this node has a VPN
	 * @return The IntroductionRequest by which we know the neighbor.
	 * @throws IOException if this node is not connected to the specified
	 *                               neighbor.
	 */
	public IntroductionRequest getIntroductionRequestForNeighbor(InetAddress nbr) throws IOException;

}
