package com.github.glfrazier.snd.simulation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

import com.github.glfrazier.snd.util.DenialReporter;

public class Statistics implements Serializable, DenialReporter {

	private static final long serialVersionUID = 1L;

	public static final String DEFAULT_BASE_DIR = ".";
	public static final String DEFAULT_RESULTS_DIR = "results";

	private Properties properties;

	private File baseDir;
	private File resultsDir;
	private IndividualStatistics stats;

	private boolean closed;
	private boolean opened;

	public Statistics(Properties props) {
		this.properties = props;
		this.stats = new IndividualStatistics();
		initialize();
	}

	private void initialize() {
		String baseDirStr = properties.getProperty("snd.stats.base_dir", DEFAULT_BASE_DIR);
		baseDir = new File(baseDirStr);
		String resultsDirStr = properties.getProperty("snd.stats.results_dir", DEFAULT_RESULTS_DIR);
		resultsDir = new File(baseDir, resultsDirStr);
		resultsDir.mkdirs();
		File subdir = null;
		for (int i = 0; true; i++) {
			String subdirName = String.format("%03d", i);
			subdir = new File(resultsDir, subdirName);
			if (!subdir.exists()) {
				subdir.mkdir();
				break;
			}
		}
		resultsDir = subdir;
	}

	/**
	 * Write the statistics to disk.
	 * 
	 * @throws IOException if statistics cannot be saved
	 */
	public synchronized void save() throws IOException {
		closed = true;
		for (Object key : properties.keySet()) {
			String name = key.toString();
			if (name.startsWith("snd.stats.name") || name.startsWith("snd.stats.desc")) {
				File f = new File(resultsDir, name + ".txt");
				PrintStream out = new PrintStream(new FileOutputStream(f));
				out.println(properties.getProperty(name));
				out.close();
			}
		}
		Map<String, Long> results = new HashMap<>();
		try {
			Field[] fields = IndividualStatistics.class.getDeclaredFields();
			for (Field field : fields) {
				String name = field.getName();
				if (name.startsWith("this")) {
					continue;
				}
				// System.out.println("field name=" + name);
				results.put(name, field.getLong(stats));
			}
		} catch (IllegalArgumentException | IllegalAccessException | SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(-1);
		}
		if (true) {
			File f = new File(resultsDir, "results.txt");
			PrintStream out = new PrintStream(new FileOutputStream(f));
			TreeSet<String> fields = new TreeSet<>();
			fields.addAll(results.keySet());
			for(String s : fields) {
				out.println(s + "=" + results.get(s));
			}
			out.close();
			System.out.println("Results written to " + f.getCanonicalPath());
		}
	}

	private void saveData(String name, double[] values, double[] mins, double[] maxes) throws IOException {
		File f = new File(resultsDir, name + ".csv");
		PrintStream out = new PrintStream(new FileOutputStream(f));
		for (int i = 0; i < values.length; i++) {
			out.println(String.format("%d \t%.4f  \t%.4f \t%.4f", i, values[i], mins[i], maxes[i]));
		}
		out.close();
	}

	private void saveData(String name, double[] values) throws IOException {
		File f = new File(resultsDir, name + ".csv");
		PrintStream out = new PrintStream(new FileOutputStream(f));
		for (int i = 0; i < values.length; i++) {
			out.println(String.format("%d \t%.4f", i, values[i]));
		}
		out.close();
	}

	@SuppressWarnings("unused")
	private class IndividualStatistics {

		public long goodMessagesSent;
		public long badMessagesSent;
		public long goodMessagesReceived;
		public long badMessagesReceived;
		public long responseToGoodMessagesReceived;
		public long responseToBadMessagesReceived;
		public long denials_1;
		public long denials_2;
		public long denials_3;
		public long denials_4;
		public long denials_5;
		public long denials_6;
		public long denials_7plus;

		public IndividualStatistics() {
		}
	}

	/**
	 * 
	 */
	public synchronized void startSimulation() {
		try {
			do {
				File f = new File(resultsDir, "properties.txt");
				PrintStream out = new PrintStream(new FileOutputStream(f));
				SortedSet<String> keys = new TreeSet<>();
				for (Object key : properties.keySet()) {
					keys.add(key.toString());
				}
				for (String key : keys) {
					out.print(key);
					out.print("=");
					out.println(properties.getProperty(key));
				}
				out.close();
			} while (false);

		} catch (Exception e) {
			System.err.println("Encountered a problem recording the state of the simulation at its start.");
			System.exit(-1);
		}
		opened = true;
	}

	private void check() {
		if (!opened || closed) {
			throw new IllegalStateException(
					"Attempting to collect stats when opened=" + opened + " and closed=" + closed + ".");
		}
	}

	public synchronized void goodMessageSent() {
		check();
		stats.goodMessagesSent++;
	}

	public synchronized void badMessageSent() {
		check();
		stats.badMessagesSent++;
	}

	public synchronized void goodMessageReceived() {
		check();
		stats.goodMessagesReceived++;
	}

	public synchronized void badMessageReceived() {
		check();
		stats.badMessagesReceived++;
	}
	
	public synchronized void responseToGoodMessageReceived() {
		check();
		stats.responseToGoodMessagesReceived++;
	}
	
	public synchronized void responseToBadMessageReceived() {
		check();
		stats.responseToBadMessagesReceived++;
	}
	
	@Override
	public synchronized void deniedAtDepth(int depth) {
		check();
		switch(depth) {
		case 0:
			throw new IllegalArgumentException("What does depth of zero even mean?");
		case 1:
			stats.denials_1++;
			break;
		case 2:
			stats.denials_2++;
			break;
		case 3:
			stats.denials_3++;
			break;
		case 4:
			stats.denials_4++;
			break;
		case 5:
			stats.denials_5++;
			break;
		case 6:
			stats.denials_6++;
			break;
			default:
		stats.denials_7plus++;
		}
	}


}