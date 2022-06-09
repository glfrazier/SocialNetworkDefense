package com.github.glfrazier.snd.util;

import java.io.IOException;
import java.net.InetAddress;

import com.github.glfrazier.snd.protocol.IntroductionRequest;

/**
 * Beyond simply being a VPN endpoint, a Communicator actively opens and closes
 * VPNs.
 * 
 * @author Greg Frazier
 *
 */
public interface Communicator extends VPNEndpoint {

	/**
	 * Get one of the long-lived VPNs. For a ClientImpl, this would be
	 * <code>getVPN(initialIntroducer)</code>.
	 * 
	 * @param nbr
	 * @return
	 */
	public VPN getVPN(InetAddress nbr);

	public VPN getVPN(IntroductionRequest request);

	public VPN openVPN(InetAddress nbr) throws IOException;

	public VPN openVPN(InetAddress nbr, IntroductionRequest request) throws IOException;

	public void closeVPN(InetAddress nbr);

	public void closeVPN(IntroductionRequest request);

}
