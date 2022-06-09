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
	 * Maintain the state of the search.
	 */
	public interface Query {

	}
}
