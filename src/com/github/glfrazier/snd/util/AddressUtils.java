package com.github.glfrazier.snd.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class AddressUtils {

	public static InetAddress ZERO_IPv4_ADDRESS;
	static {
		try {
			final byte[] FOUR_ZEROS = { 0, 0, 0, 0 };
			ZERO_IPv4_ADDRESS = InetAddress.getByAddress(FOUR_ZEROS);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
	public static InetAddress ZERO_IPv6_ADDRESS;
	static {
		final byte[] SIXTEEN_ZEROS = { 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0 };
		try {
			ZERO_IPv6_ADDRESS = InetAddress.getByAddress(SIXTEEN_ZEROS);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public static InetAddress incrementAddress(InetAddress addr) {
		byte[] bytes = addr.getAddress();
		int index = bytes.length - 1;
		bytes[index]++;
		while (bytes[index] == 0) {
			index -= 1;
			if (index == 0) {
				throw new IllegalArgumentException("Cannot increment the MSB if the address!");
			}
			bytes[index]++;
		}
		InetAddress result = null;
		try {
			result = InetAddress.getByAddress(bytes);
			return result;
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		return result;
	}
}
