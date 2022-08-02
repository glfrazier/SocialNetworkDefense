package com.github.glfrazier.snd.util;

import java.net.InetAddress;

public interface DiscoveryService {

	/**
	 * Construct a discovery service query, looking for the next step(s) from src to
	 * dst.
	 * 
	 * @param dst the address one wants to reach
	 * @return a query to use against the discovery system
	 */
	public Query createQuery(InetAddress dst);

	/**
	 * Returns a feasible next step to reach the desired destination. The query is
	 * modified to reflect this request, so that one can repeatedly invoke
	 * {@link #getNextHopTo(Query)} and, if there are multiple next steps, get a
	 * different next step. Returns {@code null} if there is no route to the
	 * destination or if all feasible routes have already been returned.
	 * 
	 * @param query
	 * @return a feasible next hop to reach the destination from source.
	 */
	public InetAddress getNextHopTo(Query query);

	/**
	 * Obtain the address of the proxy for the destination. Returns the address of
	 * the destination if the destination is itself in the SND network (i.e.,
	 * requires no proxy).
	 * 
	 * @param dst the destination address of a message
	 * @return the argument <code>dst</code> if the destination is in the network;
	 *         or the address of the destination's proxy; or <code>null</code> if
	 *         the provided address is neither part of the network nor has a
	 *         registered proxy.
	 */
	public InetAddress getProxyFor(InetAddress dst);

	/**
	 * Maintain the state of the search.
	 */
	public interface Query {

	}
}
