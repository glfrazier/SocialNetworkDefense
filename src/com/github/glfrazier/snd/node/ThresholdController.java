package com.github.glfrazier.snd.node;

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventProcessor;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.protocol.Feedback;

public class ThresholdController implements EventProcessor {

	/** How frequently the threshold is adjusted. */
	private static final long THRESHOLD_UPDATE_INTERVAL = 5000; // Every 5 seconds

	private static final float HEAD_SPACE = 0.1f;

	private static Logger LOGGER = Logger.getLogger(ThresholdController.class.getName());

	private final ReputationModule reputationModule;
	private final EventingSystem eventingSystem;
	private final String owner;
	private float threshold;

	private Event THRESHOLD_UPDATE_EVENT = new Event() {
		public String toString() {
			return "Thold Update";
		}
	};
	
	// Parameters that specify controller behavior
	private final double K;
	private final double tauI;
	private final double tauT;
	private final double sensorSmoothingAlpha;
	
	// Controller state
	private double controllerState = -1.0;
	private double health;
	private double targetHealth;
	private double actuatorError;
	private long timeOfLastThresholdUpdate;


	public ThresholdController(ReputationModule repModule, EventingSystem es, SNDNode owner) {
		this.reputationModule = repModule;
		this.eventingSystem = es;
		this.owner = owner.toString();
		K = owner.getFloatProperty("snd.thold_ctlr.K");
		tauI = owner.getFloatProperty("snd.thold_ctlr.tau_i");
		tauT = owner.getFloatProperty("snd.thold_ctlr.tau_t");
		sensorSmoothingAlpha = owner.getFloatProperty("snd.thold_ctlr.sensor_smoothing_alpha");
		if (tauI == 0.0) {
			throw new IllegalArgumentException("snd.thold_ctlr.tau_i cannot be zero.");
		}
		if (K == 0.0) {
			throw new IllegalArgumentException("snd.thold_ctlr.K cannot be zero.");
		}
		timeOfLastThresholdUpdate = eventingSystem.getCurrentTime();
		eventingSystem.scheduleEventRelative(this, THRESHOLD_UPDATE_EVENT, THRESHOLD_UPDATE_INTERVAL);
	}

	public synchronized float getThreshold() {
		return threshold;
	}

	@Override
	public void process(Event e, EventingSystem eventingSystem) {
		if (e == THRESHOLD_UPDATE_EVENT) {
			updateThreshold();
			eventingSystem.scheduleEventRelative(this, e, THRESHOLD_UPDATE_INTERVAL);
		}
	}

	public synchronized void updateHealth(Feedback eventFeedback) {
		double a = sensorSmoothingAlpha;
		double sensor = (eventFeedback == Feedback.BAD ? 0.0 : 1.0);
		health = (a * sensor) + ((1 - a) * health);
		if (eventFeedback == Feedback.BAD) {
			updateThreshold();
		}
	}

	private synchronized void updateThreshold() {
		double healthError = targetHealth - health;
		double unconstrainedThreshold = unconstrainedControllerUpdate(healthError, actuatorError);
		double upperBound = 0.0;
		double lowerBound = reputationModule.getLeastReputation() - HEAD_SPACE;
		double constrainedThreshold = max(min(unconstrainedThreshold, upperBound), lowerBound);
		actuatorError = constrainedThreshold - unconstrainedThreshold;
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer(String.format(
					"health %.2f | health_err %.2f | uthresh %.2f | cthresh %.2f | lb %.2f | ub %.2f | actuator error %.2f",
					health, healthError, unconstrainedThreshold, constrainedThreshold, lowerBound, upperBound,
					actuatorError));
		}
		threshold = (float) constrainedThreshold;
	}

	private double unconstrainedControllerUpdate(double healthErrorArg, double actuatorErrorArg) {
		long now = eventingSystem.getCurrentTime();
		double dt = ((double) (now - timeOfLastThresholdUpdate)) / 1000.0;
		timeOfLastThresholdUpdate = now;

		// If there is no health error, then we are "doing the right thing". So, control
		// == controllerState. Else, respond to the health error, modulated by K.
		double control = controllerState + K * healthErrorArg;

		// If health error and actuator error are both zero, controller state remains
		// the same.
		//
		// If health error and/or actuator error are positive, then we increase the
		// controller state, which will increase the control on the next iteration.
		// Similarly, if they are negative, then the controller state is decreased.
		//
		// K increases the amount that health error impacts control and increases the
		// amount that health error impacts controller state at the expense of actuator
		// error.
		//
		// tauI decreases the impact of health error on controller state and
		// correspondingly increases the impact of actuator error. It is exactly the
		// inverse of K, except that K impacts the control, too, while tauI does not.
		//
		// tauT increases the impact of actuator error on controller state without
		// decreasing the impact of health or impacting control.
		//
		// Increase K to make the controller respond more quickly to health issues,
		// possibly ignoring actuator error.
		//
		// Increase tauT to make the system respond more quickly to actuator error.
		//
		// Increase tauI to make the system respond to actuator error, reducing the
		// impact of health error.
		//
		controllerState = controllerState + dt * K * healthErrorArg / tauI + dt * tauT * tauI * actuatorErrorArg / K;
		return control;

	}

	@Override
	public String toString() {
		return "Threshold Ctlr for " + owner;
	}
}
