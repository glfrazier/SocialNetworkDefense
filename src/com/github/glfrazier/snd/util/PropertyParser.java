package com.github.glfrazier.snd.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

public class PropertyParser {

	/**
	 * Parse a property that specifies an integer.
	 * 
	 * @param propName the name of the property
	 * @return the value of the property
	 */
	public static int getIntegerProperty(String propName, Properties properties) {
		String iStr = properties.getProperty(propName);
		if (iStr == null) {
			System.err.println("'" + propName + "' was not specified in the provided properties; it is required.");
			System.exit(-1);
		}
		try {
			int i = Integer.parseInt(iStr);
			return i;
		} catch (NumberFormatException e) {
			System.err
					.println("The value for the property '" + propName + "' is not an integer number---it is " + iStr);
			System.exit(-1);
		}
		// unreachable code
		return 0;
	}

	/**
	 * Parse a property that specifies an integer.
	 * 
	 * @param propName the name of the property
	 * @return the value of the property
	 */
	public static float getFloatProperty(String propName, Properties properties) {
		String fStr = properties.getProperty(propName);
		if (fStr == null) {
			System.err.println("'" + propName + "' was not specified in the provided properties; it is required.");
			System.exit(-1);
		}
		try {
			float f = Float.parseFloat(fStr);
			return f;
		} catch (NumberFormatException e) {
			System.err.println(
					"The value for the property '" + propName + "' is not a floating-point number---it is " + fStr);
			System.exit(-1);
		}
		// unreachable code
		return 0;
	}

	public static boolean getBooleanProperty(String propName, boolean defaultValue, Properties properties) {
		return getBooleanProperty(propName, Boolean.toString(defaultValue), properties);
	}

	public static boolean getBooleanProperty(String propName, String defaultValue, Properties properties) {
		if (!properties.containsKey(propName)) {
			properties.setProperty(propName, defaultValue);
		}
		return properties.getProperty(propName).equalsIgnoreCase("true");
	}

	public static String[] getListProperty(String propName, Properties properties) {
		if (!properties.containsKey(propName)) {
			System.err.println("'" + propName + "' was not specified in the provided properties; it is required.");
			System.exit(-1);
		}
		String s = properties.getProperty(propName);
		String[] listProp = s.split(",");
		for (int i = 0; i < listProp.length; i++) {
			listProp[i] = listProp[i].trim();
		}
		return listProp;
	}

	public static String[] getListProperty(String propName, String defaultValue, Properties properties) {
		if (!properties.containsKey(defaultValue)) {
			properties.setProperty(propName, defaultValue);
		}
		return getListProperty(propName, properties);
	}

	public static InetAddress getIPAddressProperty(String propName, String defaultValue, Properties properties) {
		if (!properties.containsKey(propName)) {
			properties.setProperty(propName, defaultValue);
		}
		return getIPAddressProperty(propName, properties);
	}

	public static InetAddress getIPAddressProperty(String propName, Properties properties) {
		if (!properties.containsKey(propName)) {
			System.err.println("'" + propName + "' was not specified in the provided properties; it is required.");
			System.exit(-1);
		}
		String s = properties.getProperty(propName);
		InetAddress addr = null;
		try {
			addr = InetAddress.getByName(s);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		return addr;
	}

	public static int getIntegerProperty(String propName, int defaultValue, Properties properties) {
		if (!properties.containsKey(propName)) {
			properties.setProperty(propName, Integer.toString(defaultValue));
		}
		return getIntegerProperty(propName, properties);
	}

	public static float getProbabilityProperty(String propName, Properties properties) {
		float prob = PropertyParser.getFloatProperty(propName, properties);
		if (prob < 0 || prob > 1) {
			throw new IllegalArgumentException("Property <" + propName + "> (" + prob
					+ ") is a probability; its value must be in the range [0..1].");
		}
		return prob;
	}

	public static long getLongProperty(String propName, Properties properties) {
		String lStr = properties.getProperty(propName);
		if (lStr == null) {
			System.err.println("'" + propName + "' was not specified in the provided properties; it is required.");
			System.exit(-1);
		}
		try {
			long l = Long.parseLong(lStr);
			return l;
		} catch (NumberFormatException e) {
			System.err
					.println("The value for the property '" + propName + "' is not a long integer---it is " + lStr);
			System.exit(-1);
		}
		// unreachable code
		return 0;
	}

}
