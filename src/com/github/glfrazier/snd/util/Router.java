package com.github.glfrazier.snd.util;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class Router {

	private Communicator owner;
	private Map<InetAddress, VPN> routers;
	private Map<InetAddress, InetAddress> routedBy;
	
	public Router(Communicator owner) {
		this.owner = owner;
		routers = new HashMap<>();
		routedBy = new HashMap<>();
	}
	
	public void addRoute(InetAddress dst, VPN route) {
		routers.put(dst, route);
		routedBy.put(route.getRemote(), dst);
	}
	
	public void removeRoute(InetAddress router) {
		InetAddress dst = routedBy.remove(router);
		if (dst == null) return;
		routers.remove(dst);
	}
	
	public VPN getRouteTo(InetAddress dst) {
		VPN vpn = owner.getVPN(dst);
		if (vpn != null) {
			return vpn;
		}
		vpn = routers.get(dst);
		if (vpn != null) {
			return vpn;
		}
		vpn = owner.getVPN(dst);
		if (vpn == null) {
			System.err.println("There is no VPN to " + dst);
		}
		return vpn;
	}
}
