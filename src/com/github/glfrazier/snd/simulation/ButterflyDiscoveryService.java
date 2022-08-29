package com.github.glfrazier.snd.simulation;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.github.glfrazier.snd.util.DiscoveryService;

public class ButterflyDiscoveryService implements DiscoveryService {

	private final int CACHE_SIZE;

	@SuppressWarnings("serial")
	private Map<InetAddress, InetAddress> cache = new LinkedHashMap<>(//
			16, // ......... Default initial capacity
			(float) 0.75, // Default expansion value
			true // ........ Re-order the map on access; the most-recently-accessed entry is
					// ..... always last
	) {
		protected boolean removeEldestEntry(Map.Entry<InetAddress, InetAddress> eldest) {
			return size() > CACHE_SIZE;
		}
	};

	private ButterflyNetwork networkModel;

	private InetAddress here;

	public ButterflyDiscoveryService(InetAddress owner, ButterflyNetwork model, int cacheSize) {
		this.here = owner;
		this.networkModel = model;
		CACHE_SIZE = cacheSize;
	}

	@Override
	public Query createQuery(InetAddress dst) {
		return new DQuery(dst);
	}

	@Override
	public InetAddress getNextHopTo(Query query) {
		DQuery dq = (DQuery) query;
		synchronized (cache) {
			if (cache.containsKey(dq.dst)) {
				InetAddress candidate = cache.get(dq.dst);
				if (!dq.priorAnswers.contains(candidate)) {
					dq.priorAnswers.add(candidate);
					return candidate;
				}
			}
		}
		Set<InetAddress> candidates = networkModel.getNextStepsTo(dq.dst, here);
		// TODO sort the candidates
		for (InetAddress addr : candidates) {
			if (!dq.priorAnswers.contains(addr)) {
				dq.priorAnswers.add(addr);
				synchronized (cache) {
					cache.put(dq.dst, addr);
				}
				return addr;
			}
		}
		return null;
	}

	private class DQuery implements DiscoveryService.Query {
		public final InetAddress dst;
		public final Set<InetAddress> priorAnswers;

		public DQuery(InetAddress dst) {
			this.dst = dst;
			priorAnswers = new HashSet<>();
		}

		public String toString() {
			return "QUERY: dst=" + dst;
		}

	}

	@Override
	public String toString() {
		return "ButterflyDiscoveryService(" + here + ")";
	}

	@Override
	public InetAddress getProxyFor(InetAddress dst) {
		return networkModel.getProxyFor(dst);
	}

}
