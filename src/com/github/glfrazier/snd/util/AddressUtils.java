package com.github.glfrazier.snd.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;

public class AddressUtils {

	public static String addrToString(InetAddress addr) {
		String result = addr.toString();
		String ww = result;
		if (ww.startsWith("/")) {
			ww = ww.substring(1);
		}
		if (ww.contains(":")) {
			String[] tokens = ww.split(":");
			int index = 1;
			for (; index < tokens.length - 1 && (!tokens[index].equals("0") && !tokens[index].equals("00")); index++)
				;
			if (index == tokens.length) {
				return result;
			}
			int start = index;
			for (; index < tokens.length - 1 && (tokens[index].equals("0") || tokens[index].equals("00")); index++)
				;
			int end = index;
			if (end == start + 1) {
				return result;
			}
			StringBuffer newResult = new StringBuffer(tokens[0]);
			for (int i = 1; i < start; i++) {
				newResult.append(':').append(tokens[i]);
			}
			newResult.append('.');
			for (int i = end; i < tokens.length; i++) {
				newResult.append('.').append(tokens[i]);
			}
			StringBuffer x = new StringBuffer("/").append(newResult);
			result = x.toString();
		}
		return result;
	}

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
		final byte[] SIXTEEN_ZEROS = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
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

	public static int compare(InetAddress a1, InetAddress a2) {
		byte[] b1 = a1.getAddress();
		byte[] b2 = a2.getAddress();
		int result = Integer.compare(b1.length, b2.length);
		if (result != 0) {
			return result;
		}
		for (int i = 0; i < b1.length; i++) {
			result = Byte.compare(b1[i], b2[i]);
			if (result != 0)
				return result;
		}
		return 0;
	}

	public static class AddressPair {
		public final InetAddress dst;
		public final InetAddress src;

		public AddressPair(InetAddress dst, InetAddress src) {
			this.dst = dst;
			this.src = src;
		}

		public int hashCode() {
			return dst.hashCode() ^ src.hashCode();
		}

		public boolean equals(Object o) {
			if (!(o instanceof AddressPair)) {
				return false;
			}
			AddressPair ap = (AddressPair) o;
			return dst.equals(ap.dst) && src.equals(ap.src);
		}
	}

}
