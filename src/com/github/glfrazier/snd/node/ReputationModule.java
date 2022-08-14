package com.github.glfrazier.snd.node;

import static com.github.glfrazier.snd.util.AddressUtils.addrToString;
import static java.lang.Math.max;
import static java.util.logging.Level.FINEST;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Logger;

import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.protocol.IntroductionRequest;
import com.github.glfrazier.snd.protocol.Pedigree;

public class ReputationModule {

	private static final Logger LOGGER = Logger.getLogger(ReputationModule.class.getName());

	private static final long MINIMUM_FADE_INTERVAL = 10000; // 10 seconds
	private static final long ONE_DAY = 24 * 60 * 60 * 1000; // milliseconds in a day
	@SuppressWarnings("unused")
	private static final long ONE_WEEK = 7 * ONE_DAY; // milliseconds in a week
	@SuppressWarnings("unused")
	private static final float ONE_DAY_HALF_LIFE = 8e-09f; // e^(-1 * ONE_DAY * ONE_DAY_HALF_LIFE) ~= 0.5
	private static final float ONE_WEEK_HALF_LIFE = 1.146e-09f; // e^(-1 * ONE_WEEK * ONE_WEEK_HALF_LIFE) ~= 0.5
	private static final float FADE_RATE = ONE_WEEK_HALF_LIFE;
	private static final float EPSILON = 0.001f;

	private static final float BAD_FEEDBACK_DECREMENT_BASE = -1;
	private static final float NOMINAL_FEEDBACK_INCREMENT_BASE = 0.0f;
	private static final float GOOD_FEEDBACK_INCREMENT_BASE = 0.1f;
	private static final float MAX_REPUTATION = 1.0f;

	private Map<InetAddress, Entity> userMap;
	private ThresholdController thresholdController;
	private final EventingSystem eventingSystem;

	private final Node owner;

	public ReputationModule(EventingSystem es, Node node) {
		this.eventingSystem = es;
		this.userMap = Collections.synchronizedMap(new HashMap<>());
		this.thresholdController = new ThresholdController(this, es, node);
		this.owner = node;
	}

//	public void feedbackReceived(Pedigree pedigree) {
//		User u = userMap.get(pedigree.getSubject());
//		InetAddress[] introducers = pedigree.getIntroducerSequence();
//		User intro = null;
//		for (int i = 1; i < introducers.length; i++) {
//			intro = userMap.get(introducers[i]);
//			if (intro == null) {
//				float rep = userMap.get(introducers[i - 1]).getReputation() - EPSILON;
//				intro = new User(introducers[i], rep);
//				userMap.put(introducers[i], intro);
//			}
//		}
//		if (u == null) {
//			u = new User(pedigree.getSubject(), intro.getReputation() - EPSILON);
//			userMap.put(pedigree.getSubject(), u);
//		}
//	}

	public boolean reputationIsGreaterThanThreshold(Pedigree pedigree, boolean verbose) {
		Entity u = null;
		// Find the minimum reputation in the pedigree
		float minRep = 0;
		u = userMap.get(pedigree.getSubject());
		if (u != null) {
			minRep = u.getReputation();
			if (LOGGER.isLoggable(FINEST)) {
				LOGGER.finest(u + " has pre-existing reputation " + minRep);
			}
		} else {
			if (LOGGER.isLoggable(FINEST)) {
				LOGGER.finest(pedigree.getSubject() + " is an unknown entity.");
			}
		}
		IntroductionRequest[] requests = pedigree.getRequestSequence();
		for (int i = requests.length; i > 0; i--) {
			Entity intro = userMap.get(requests[i - 1].introducer);
			if (intro != null) {
				float rep = intro.getReputation();
				if (rep < minRep) {
					minRep = rep;
					if (LOGGER.isLoggable(FINEST)) {
						LOGGER.finest("\tintroducer " + intro + " has lowered the reputation. It is now: " + minRep);
					}
				}
			}
		}

		// Compare the client's reputation to the threshold
		float threshold = thresholdController.getThreshold();
		boolean result = minRep > threshold;
		if (LOGGER.isLoggable(FINEST)) {
			LOGGER.finest(this + ": result for " + addrToString(pedigree.entity) + ": minRep=" + minRep + ", threshold="
					+ threshold + ", result=" + result);
		}

		// System.out.println(this + ": minRep=" + minRep + ", threshold=" + threshold);
		if (!result) {
			LOGGER.finest("\t\tAnd we are not creating an entry!");
			return result; // return without initializing any null User objects, if we are not accepting
							// the introduction.
		}

		// Assign a reputation to every member of the pedigree that does not
		// already have a reputation. Note that we are accepting this connection.
		for (int i = requests.length; i > 0; i--) {
			Entity intro = userMap.get(requests[i - 1].introducer);
			if (intro == null) {
				minRep = max(minRep - EPSILON, threshold + EPSILON);
				intro = new Entity(requests[i - 1].introducer, minRep);
				userMap.put(requests[i - 1].introducer, intro);
				if (LOGGER.isLoggable(FINEST)) {
					LOGGER.finest(this + ": initializing " + requests[i - 1].introducer + " to " + minRep);
				}
			}
		}
		if (u == null) {
			minRep = max(minRep - EPSILON, threshold + EPSILON);
			u = new Entity(pedigree.getSubject(), minRep);
			userMap.put(pedigree.getSubject(), u);
		}

		return result;
	}

	public void applyFeedback(Pedigree pedigree, Feedback feedback) {
		// Apply feedback to the entire pedigree. The feedback has the greatest impact
		// on the subject of the pedigree, but each introducer is also impacted. Note
		// that introducers must reject introductions that have excessively long
		// pedigrees, as a nefarious introducer could use a fake pedigree to minimize
		// the impact of bad behavior on its own reputation.
		float dRep = 0;
		switch (feedback) {
		case BAD:
			dRep = BAD_FEEDBACK_DECREMENT_BASE;
			break;
//		case NOT_BAD:
//			dRep = NOMINAL_FEEDBACK_INCREMENT_BASE;
//			break;
//		case GOOD:
//			dRep = GOOD_FEEDBACK_INCREMENT_BASE;
//			break;
//		case NOOP:
//			dRep = 0;
//			break;
		}
		adjustReputation(pedigree.getSubject(), dRep);
		dRep /= 4;
		IntroductionRequest[] requests = pedigree.getRequestSequence();
		for (int i = 0; i < requests.length; i++) {
			adjustReputation(requests[i].introducer, dRep);
			dRep /= 4;
		}
		System.out.println(owner.addTimePrefix(this + ": concerning " + addrToString(pedigree.getSubject()) + ": rep="
				+ userMap.get(pedigree.getSubject()).reputation + ", thold=" + thresholdController.getThreshold()));
	}

	private void adjustReputation(InetAddress e, float dRep) {
		Entity entity = userMap.get(e);
		if (entity == null) {
			LOGGER.warning("Asked to adjust the reputation of entity " + e
					+ ", but the ReputationModule has no record of that entity.");
			return;
		}
		entity.adjustReputation(dRep);
	}

	public synchronized Collection<Entity> getUsers() {
		return userMap.values();
	}

	class Entity implements Comparable<Entity> {

		InetAddress identity;
		long timeCreated;
		long timeOfLastFeedback;
		long timeLastFaded;
		float reputation;

		public Entity(InetAddress id, float initialReputation) {
			this(id);
			reputation = initialReputation;
		}

		private Entity(InetAddress id) {
			identity = id;
			timeCreated = eventingSystem.getCurrentTime();
		}

		public synchronized void adjustReputation(float dRep) {
			timeOfLastFeedback = eventingSystem.getCurrentTime();
			fade(); // apply the fade
			reputation += dRep;
			if (reputation > MAX_REPUTATION) {
				reputation = MAX_REPUTATION;
			}
		}

		private void fade() {
			if (reputation < 0) {
				// only fade negative reputations
				long now = eventingSystem.getCurrentTime();
				float fadeTime = now - timeLastFaded;
				if (fadeTime > MINIMUM_FADE_INTERVAL) {
					reputation *= Math.exp(-1 * FADE_RATE * fadeTime);
					timeLastFaded = now;
				}
			}
		}

		public synchronized float getReputation() {
			fade();
			return reputation;
		}

		@Override
		public int hashCode() {
			return identity.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			Entity ou = (Entity) o;
			return identity.equals(ou.identity);
		}

		@Override
		public int compareTo(Entity o) {
			return Float.compare(reputation, o.reputation);
		}

		@Override
		public String toString() {
			return "Entity " + addrToString(identity);
		}
	}

	public float getLeastReputation() {
		// TODO make this a less-expensive operation by keeping the Users in bins, so we
		// only need to sort the lowest bin. Actually, we just need one bin that has the
		// lowest N Users in it. If the bin empties or becomes too big, we adjust the
		// upper boundary of this low-reputation cache.
		if (userMap.isEmpty()) {
			return EPSILON;
		}
		TreeSet<Entity> sorted = new TreeSet<>();
		sorted.addAll(userMap.values());
		return sorted.iterator().next().getReputation();
	}

	@Override
	public String toString() {
		return "RepModule for " + owner;
	}

}
