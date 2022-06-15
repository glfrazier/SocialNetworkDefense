package com.github.glfrazier.snd.util;

/**
 * Unify all of the platform-specific implementations behind one interface. To
 * port the SNDNode to a simulation, to Android, to IOS, etc., one specifies the
 * class that implements this interface. That class is then responsible for
 * providing the instantiations of the services provided by this class' getters.
 */
public interface Implementation {

	public Comms getComms();
	
	public DiscoveryService getDiscoveryService();

	public VPNFactory getVpnFactory();

}
