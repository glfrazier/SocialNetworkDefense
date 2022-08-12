package com.github.glfrazier.snd.simulation;

import static com.github.glfrazier.snd.util.AddressUtils.incrementAddress;
import static com.github.glfrazier.snd.util.AddressUtils.addrToString;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ButterflyNetwork {

	private int numRows;
	private int numColumns;
	private int numPorts;
	/**
	 * The matrix is addressed like a numpy matrix: (row, col)
	 */
	private NodeModel[][] matrix;
	private Map<NodeModel, Location> locationMap;
	private Map<InetAddress, NodeModel> addressMap;
	private Map<InetAddress, InetAddress> proxyConnections = new HashMap<>();
	private Map<InetAddress, InetAddress> proxies;

	public ButterflyNetwork(int fanout, int height, int width, InetAddress baseAddress) {
		numRows = height;
		numColumns = width;
		numPorts = fanout;
		matrix = new NodeModel[numRows][numColumns];
		locationMap = new HashMap<>();
		addressMap = new HashMap<>();
		this.proxies = new HashMap<>();
		buildNetwork(baseAddress);
	}

	public InetAddress getFirstAddress() {
		return matrix[0][0].address;
	}

	public InetAddress getLastAddress() {
		return matrix[numRows - 1][numColumns - 1].address;
	}

	public InetAddress[] getFirstColumn() {
		InetAddress[] col = new InetAddress[numRows];
		for (int i = 0; i < col.length; i++) {
			col[i] = matrix[i][0].address;
		}
		return col;
	}

	public InetAddress[] getLastColumn() {
		InetAddress[] col = new InetAddress[numRows];
		for (int i = 0; i < col.length; i++) {
			col[i] = matrix[i][numColumns - 1].address;
		}
		return col;
	}

	private void buildNetwork(InetAddress baseAddress) {
		InetAddress addr = baseAddress;
		for (int row = 0; row < numRows; row++) {
			for (int col = 0; col < numColumns; col++) {
				matrix[row][col] = new NodeModel( //
						addr, // ................................ address
						(col == 0 ? 0 : numPorts), // ........... # input ports
						(col == numColumns - 1 ? 0 : numPorts) // # output ports
				);
				locationMap.put(matrix[row][col], new Location(row, col));
				addressMap.put(matrix[row][col].address, matrix[row][col]);
				addr = incrementAddress(addr);
			}
		}
		for (int col = 0; col < numColumns - 1; col++) {
			int inPort = 0;
			int rowN = 0;
			for (int row = 0; row < numRows; row++) {
				for (int outPort = 0; outPort < numPorts; outPort++) {
					matrix[row][col].link(outPort, matrix[rowN][col + 1], inPort);
					rowN++;
					if (rowN == numRows) {
						rowN = 0;
						inPort++;
					}
				}
			}
		}
	}

	public Set<InetAddress> getNextStepsTo(InetAddress end, InetAddress start) {
		Set<InetAddress> result = new HashSet<>();

		if (end.equals(start)) {
			result.add(end);
			return result;
		}
		InetAddress intro = null;
		if (addressMap.containsKey(end)) {
			intro = end; } else { intro = proxyConnections.get(end);
		}
		if (intro == null) {
			System.out.println("In getNextStepsTo(), end=" + addrToString(end) + ", start=" + addrToString(start)
					+ ", intro=null");
		}
		if (intro.equals(start)) {
			result.add(end);
			return result;
		}
		NodeModel node = addressMap.get(intro);
		Location location = locationMap.get(node);
		int lastIntroducerColumn = location.col;
		if (lastIntroducerColumn == 0) {
			result = recursiveGetPreviousStepsTo(intro, start);
		} else if (lastIntroducerColumn == numColumns - 1) {
			result = recursiveGetNextStepsTo(intro, start);
		} else {
			new Exception("Communicating host " + addrToString(end) + " is connected to a weird column: "
					+ lastIntroducerColumn).printStackTrace();
			System.exit(-1);
		}
		return result;
	}

	/**
	 * The network flows from left-to-right. The end is at the right, the start is
	 * somewhere to the left of the end. We consider the in-ports of the end, which
	 * is how nodes to the left enter the end. If a node to the left of the end is
	 * the start, then the end is part of the result. Otherwise, the node to the
	 * left of the end (which can reach the end) is the new end, and we recursively
	 * consider it.
	 * 
	 * @param end
	 * @param start
	 * @return
	 */
	private Set<InetAddress> recursiveGetNextStepsTo(InetAddress end, InetAddress start) {
		Set<InetAddress> result = new HashSet<>();
		NodeModel dstIntroducer = addressMap.get(end);
		for (int i = 0; i < dstIntroducer.inPorts.length; i++) {
			NodeModel p = dstIntroducer.inPorts[i];
			if (p.address.equals(start)) {
				result.add(end);
			} else {
				result.addAll(recursiveGetNextStepsTo(p.address, start));
			}
		}
		return result;
	}

	/**
	 * The network flows from left-to-right. The end is at the left, the start is
	 * somewhere to the right of the end. We consider the out-ports of the end,
	 * which is how nodes to the right enter the end (we are going against the flow,
	 * in contrast to {@link #recursiveGetNextStepsTo(InetAddress, InetAddress)}. If
	 * a node to the right of the end is the start, then the end is part of the
	 * result. Otherwise, the node to the right of the end (which can reach the end)
	 * is the new end, and we recursively consider it.
	 * 
	 * @param end
	 * @param start
	 * @return
	 */
	private Set<InetAddress> recursiveGetPreviousStepsTo(InetAddress end, InetAddress start) {
		Set<InetAddress> result = new HashSet<>();
		NodeModel dstIntroducer = addressMap.get(end);
		for (int i = 0; i < dstIntroducer.outPorts.length; i++) {
			NodeModel p = dstIntroducer.outPorts[i];
			if (p.address.equals(start)) {
				result.add(end);
			} else {
				result.addAll(recursiveGetPreviousStepsTo(p.address, start));
			}
		}
		return result;
	}

	private class Location {
		public final int row;
		public final int col;

		public Location(int r, int c) {
			row = r;
			col = c;
		}

		public int hashCode() {
			return Integer.hashCode(row) + Integer.hashCode(col);
		}

		public boolean equals(Object o) {
			if (!(o instanceof Location))
				return false;
			Location l = (Location) o;
			return l.row == row && l.col == col;
		}
	}

	private class NodeModel {
		private InetAddress address;
		private NodeModel[] inPorts;
		private NodeModel[] outPorts;
		private Integer firstNbrRow;
		private Integer lastNbrRow;
		private boolean wrapsAround;

		public NodeModel(InetAddress addr, int fanIn, int fanOut) {
			address = addr;
			inPorts = new NodeModel[fanIn];
			outPorts = new NodeModel[fanOut];
		}

		public boolean isReachableNextHop(int row) {
			if (firstNbrRow == null) {
				NodeModel firstNbr = outPorts[0];
				NodeModel lastNbr = outPorts[outPorts.length - 1];
				firstNbrRow = locationMap.get(firstNbr).row;
				lastNbrRow = locationMap.get(lastNbr).row;
				if (lastNbrRow < firstNbrRow)
					wrapsAround = true;
			}
			if (wrapsAround) {
				return row >= firstNbrRow || row <= lastNbrRow;
			} else {
				return row >= firstNbrRow && row <= lastNbrRow;
			}
		}

		public void link(int outPort, NodeModel nbr, int inPort) {
			outPorts[outPort] = nbr;
			nbr.inPorts[inPort] = this;
		}

		public int hashCode() {
			return address.hashCode();
		}

		public boolean equals(Object o) {
			if (!(o instanceof NodeModel))
				return false;
			return ((NodeModel) o).address.equals(address);
		}

	}

	public InetAddress getAddressOfElement(int row, int col) {
		return matrix[row][col].address;
	}

	public boolean areConnected(InetAddress a, InetAddress b) {
		NodeModel pA = addressMap.get(a);
		for (int i = 0; i < pA.inPorts.length; i++) {
			if (pA.inPorts[i].address.equals(b)) {
				return true;
			}
		}
		for (int i = 0; i < pA.outPorts.length; i++) {
			if (pA.outPorts[i].address.equals(b)) {
				return true;
			}
		}
		return false;
	}

	public void connectProxy(InetAddress serverAddress, InetAddress introAddr) {
		proxyConnections.put(serverAddress, introAddr);
	}

	public void setProxyFor(InetAddress dst, InetAddress proxy) {
		proxies.put(dst, proxy);
	}

	public InetAddress getProxyFor(InetAddress dst) {
		if (!proxies.containsKey(dst)) {
			return dst;
		}
		return proxies.get(dst);
	}

}
