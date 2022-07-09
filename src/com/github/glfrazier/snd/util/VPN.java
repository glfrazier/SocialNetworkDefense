package com.github.glfrazier.snd.util;

import java.io.IOException;

import com.github.glfrazier.snd.node.Link;
import com.github.glfrazier.snd.protocol.message.Message;

/**
 * A VPN sends messages. Receive occurs via {@link Link#receive(Message)} callback.
 * 
 * @see VPNFactory#createVPN(java.net.InetAddress, Object)
 * @see VPNFactory#createIntroducedVPN(java.net.InetAddress,
 *      com.github.glfrazier.snd.protocol.IntroductionRequest, Object)
 */
public interface VPN {

	public void send(Message m) throws IOException;

	public void close();
	
}
