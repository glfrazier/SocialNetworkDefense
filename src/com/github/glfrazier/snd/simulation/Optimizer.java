package com.github.glfrazier.snd.simulation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import com.github.glfrazier.snd.util.PropertyParser;

public class Optimizer {

	private static final String[] CHANGING_PROPERTIES = { //
			"snd.thold_ctler.target_health", //
			"snd.thold_ctlr.K", //
			"snd.thold_ctlr.sensor_smoothing_alpha", //
			"snd.thold_ctlr.tau_i", //
			"snd.thold_ctlr.tau_t"//
	};
	private Map<Properties, Float> props;
	private Properties bestProps;
	private float bestScore;
	private int numTrials;
	private Random random;
	private String bestResultsFile;

	public Optimizer(Properties properties) throws IOException {
		bestProps = properties;
		props = new HashMap<Properties, Float>();
		random = new Random();
		numTrials = PropertyParser.getIntegerProperty("snd.sim.optimizer.number_of_trials", properties);
	}

	private Simulation runSimulation(Properties candidate) {
		Simulation sim = null;
		try {
			sim = new Simulation(candidate);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		sim.run();
		return sim;
	}

	public void run() {
		Properties candidate = bestProps;
		Simulation sim = runSimulation(candidate);
		Properties results = getResults(sim);
		String resultsFile = getResultsFile(sim);
		float score = calculateScore(results);
		props.put(candidate, score);
		bestScore = score;
		bestResultsFile = resultsFile;
		float candidateScore = bestScore;
		File resultsDir = new File(resultsFile).getParentFile();
		saveScore(candidateScore, resultsDir);
		String changedProperty = null;
		System.out.println("Baseline: score=" + bestScore);
		for (int i = 0; i < numTrials; i++) {
			if (candidateScore > bestScore) {
				Properties newProps = continueChange(bestProps, candidate, changedProperty);
				bestProps = candidate;
				bestScore = candidateScore;
				bestResultsFile = getResultsFile(sim);
				candidate = newProps;
			} else {
				candidate = new Properties();
				changedProperty = randomChange(bestProps, candidate);
			}
			sim = runSimulation(candidate);
			results = getResults(sim);
			resultsFile = getResultsFile(sim);
			candidateScore = calculateScore(results);
			resultsDir = new File(resultsFile).getParentFile();
			saveScore(candidateScore, resultsDir);
			props.put(candidate, candidateScore);
			System.out.println("Trial " + i + ": modified " + changedProperty + ", score=" + candidateScore);
		}
		System.out.println("The best results are in <" + bestResultsFile + ">");
	}

	private void saveScore(float candidateScore, File resultsDir) {
		File f = new File(resultsDir, "score.txt");
		try {
			FileOutputStream fout = new FileOutputStream(f);
			fout.write(Float.toString(candidateScore).getBytes());
			fout.write('\n');
			fout.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private Properties continueChange(Properties previous, Properties current, String changedProperty) {
		float oldValue = Float.parseFloat(previous.getProperty(changedProperty));
		float currentValue = Float.parseFloat(current.getProperty(changedProperty));
		float newValue = currentValue + (currentValue - oldValue);
		if (isPropbabilityProperty(changedProperty)) {
			if (newValue > 1 - 10e-5f) {
				newValue = 1 - 10e-5f;
			} else if (newValue < 10e-5f) {
				newValue = 10e-5f;
			}
		}
		Properties newProps = new Properties();
		newProps.putAll(current);
		newProps.setProperty(changedProperty, Float.toString(newValue));
		return newProps;
	}

	private String randomChange(Properties baseProps, Properties candidate) {
		candidate.clear();
		String changedProp = CHANGING_PROPERTIES[random.nextInt(CHANGING_PROPERTIES.length)];
		float value = Float.parseFloat(baseProps.getProperty(changedProp));
		value = adjustValue(value);
		if (isPropbabilityProperty(changedProp)) {
			if (value > 1 - 10e-5f) {
				value = 1 - 10e-5f;
			} else if (value < 10e-5f) {
				value = 10e-5f;
			}
		}
		candidate.putAll(baseProps);
		candidate.setProperty(changedProp, Float.toString(value));
		return changedProp;
	}

	private boolean isPropbabilityProperty(String prop) {
		return prop.equals(CHANGING_PROPERTIES[0]) || prop.equals(CHANGING_PROPERTIES[2]);
	}

	private float adjustValue(float value) {
		float dir = random.nextBoolean() ? 1 : -1;
		return value + dir * 0.01f * value;
	}

	private float calculateScore(Properties results) {
		int numGoodMessagesReceived = Integer.parseInt(results.getProperty("goodMessagesReceived"));
		int numGoodMessageResponses = Integer.parseInt(results.getProperty("responseToGoodMessagesReceived"));
		int badMessagesDelivered = Integer.parseInt(results.getProperty("badMessagesReceived"));
		return numGoodMessagesReceived + numGoodMessageResponses - (100 * badMessagesDelivered);
	}

	private String getResultsFile(Simulation sim) {
		return sim.getProperties().getProperty("results.txt");
	}

	private Properties getResults(Simulation sim) {
		Properties results = new Properties();
		FileInputStream in;
		try {
			in = new FileInputStream(getResultsFile(sim));
			results.load(in);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		return results;
	}

	public static void main(String[] args) throws Exception {
		Properties properties = PropertyParser.parseCmdLine(args, "snd");
		Optimizer opt = new Optimizer(properties);
		opt.run();
	}
}
