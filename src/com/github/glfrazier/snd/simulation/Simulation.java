package com.github.glfrazier.snd.simulation;

import static com.github.glfrazier.snd.util.AddressUtils.incrementAddress;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import com.github.glfrazier.event.Event;
import com.github.glfrazier.event.EventProcessor;
import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.ClientProxy;
import com.github.glfrazier.snd.node.SNDNode;
import com.github.glfrazier.snd.node.ServerProxy;
import com.github.glfrazier.snd.util.DiscoveryService;
import com.github.glfrazier.snd.util.PropertyParser;
import com.github.glfrazier.snd.util.VPN;
import com.github.glfrazier.snd.util.VPNFactory;

public class Simulation {

	private EventingSystem eventingSystem;
	private Properties properties;
	private List<ClientProxy> clients = new ArrayList<>();
	private List<ServerProxy> servers = new ArrayList<>();
	private Set<TrafficGenerator> appClients = new HashSet<>();
	private Set<TrafficGenerator> goodAppClients;
	private Set<TrafficGenerator> badAppClients;
	private TrafficReceiver[] appServers;
	private Random simRandom;

	public Simulation(Properties properties) {
		this.properties = properties;

		// load the properties
		Properties sysProps = new Properties();
		for (Object key : System.getProperties().keySet()) {
			String name = key.toString();
			if (!name.startsWith("snd.")) {
				continue;
			}
			sysProps.put(name, System.getProperty(name));
		}
		addPropertiesIfNotAlreadyThere(properties, sysProps);
		while (properties.containsKey("snd.properties_file")) {
			String filename = properties.remove("snd.properties_file").toString();
			System.out.println("Loading properties from <" + filename + ">");
			Properties newProps = null;
			try {
				FileInputStream in = new FileInputStream(filename);
				newProps = new Properties();
				newProps.load(in);
				in.close();
			} catch (Exception e) {
				System.err.println("Failure to load properties from file <" + filename + ">: " + e);
				e.printStackTrace();
			}
			addPropertiesIfNotAlreadyThere(properties, newProps);
		}
		simRandom = new Random();
		if (properties.containsKey("snd.sim.seed")) {
			simRandom.setSeed(getLongProperty("snd.sim.seed"));
		}

		// Construct the eventing system. Since this is a simulation, we are *NOT*
		// running the EventingSystem in realtime.
		eventingSystem = new EventingSystem(EventingSystem.NOT_REALTIME);
		long endTime = getLongProperty("snd.sim.end_time");
		eventingSystem.setEndTime(endTime);
		System.out.println("Simulation will end at time " + endTime);
		System.out.println("===========================================");

		// build the introducer network
		Map<InetAddress, SNDNode> introducerMap = new HashMap<>();
		ButterflyNetwork topology = null;
		int rowsOfIntroducers = getIntegerProperty("snd.sim.number_of_introducer_rows");
		int colsOfIntroducers = getIntegerProperty("snd.sim.number_of_introducer_cols");
		if (true) {
			int cacheSize = getIntegerProperty("snd.discover_service.cache_size");
			int fanout = getIntegerProperty("snd.sim.introducer_fanout");
			InetAddress baseAddress = buildBaseAddress();
			topology = new ButterflyNetwork(fanout, rowsOfIntroducers, colsOfIntroducers, baseAddress);
			List<SNDNode> introducers = new ArrayList<>(rowsOfIntroducers * colsOfIntroducers);
			for (int row = 0; row < rowsOfIntroducers; row++) {
				for (int col = 0; col < colsOfIntroducers; col++) {
					InetAddress address = topology.getAddressOfElement(row, col);
					DiscoveryService discoveryService = new ButterflyDiscoveryService(address, topology, cacheSize);
					VPNFactory vpnFactory = new SimVPNFactory(eventingSystem);
					SNDNode introducer = new SNDNode(address, vpnFactory, discoveryService, eventingSystem, properties);
					introducers.add(introducer);
					introducerMap.put(address, introducer);
				}
			}
			for (int i = 0; i < introducers.size() - 1; i++) {
				for (int j = i + 1; j < introducers.size(); j++) {
					SNDNode ii = introducers.get(i);
					SNDNode ij = introducers.get(j);
					if (topology.areConnected(ii.getAddress(), ij.getAddress())) {
						try {
							ii.openVPN(ij.getAddress());
							ij.openVPN(ii.getAddress());
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							System.exit(-1);
						}
					}
				}
			}
		}

		// construct server proxies and link them to appropriate introducers
		InetAddress firstServerAddress = incrementAddress(topology.getLastAddress());
		InetAddress lastServerAddress = null;
		int numberOfServers = getIntegerProperty("snd.sim.number_of_servers");
		if (true) {
			InetAddress serverAddress = firstServerAddress;
			int index = 0;
			for (int i = 0; i < numberOfServers; i++) {
				VPNFactory vpnFactory = new SimVPNFactory(eventingSystem);
				ServerProxy serverProxy = new ServerProxy(serverAddress, vpnFactory, eventingSystem, properties);
				servers.add(serverProxy);
				InetAddress introAddr = topology.getAddressOfElement(index, colsOfIntroducers - 1);
				SNDNode introImpl = introducerMap.get(introAddr);
				try {
					serverProxy.openVPN(introAddr);
					introImpl.openVPN(serverAddress);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(-1);
				}
				topology.connectServerProxy(serverAddress, introAddr);
				lastServerAddress = serverAddress;
				serverAddress = incrementAddress(serverAddress);
				index++;
				if (index == rowsOfIntroducers) {
					index = 0;
				}
			}
		}

		// construct client proxies and link them to appropriate introducers
		int numberOfClients = getIntegerProperty("snd.sim.number_of_clients");
		InetAddress firstClientAddress = incrementAddress(lastServerAddress);
		InetAddress lastClientAddress = null;
		if (true) {
			InetAddress clientAddress = firstClientAddress;
			int index = 0;
			for (int i = 0; i < numberOfClients; i++) {
				VPNFactory vpnFactory = new SimVPNFactory(eventingSystem);
				ClientProxy clientProxy = new ClientProxy(clientAddress, vpnFactory, eventingSystem, properties);
				clients.add(clientProxy);
				InetAddress introAddr = topology.getAddressOfElement(index, 0);
				SNDNode introImpl = introducerMap.get(introAddr);
				try {
					clientProxy.connectInitialIntroducer(introAddr);
					introImpl.openVPN(clientAddress);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(-1);
				}
				lastClientAddress = clientAddress;
				clientAddress = incrementAddress(clientAddress);
				index++;
				if (index == rowsOfIntroducers) {
					index = 0;
				}
			}
		}

		// construct app servers (TrafficReceivers) and connect them to ServerProxies
		InetAddress firstReceiverAddress = incrementAddress(lastClientAddress);
		InetAddress lastReceiverAddress = null;
		float falsePositive = getFloatProperty("snd.sim.sensorFP");
		float falseNegative = getFloatProperty("snd.sim.sensorFN");
		InetAddress receiverAddress = firstReceiverAddress;
		appServers = new TrafficReceiver[servers.size()];
		int index = 0;
		for (ServerProxy s : servers) {
			TrafficReceiver receiver = new TrafficReceiver(receiverAddress, falsePositive, falseNegative, this);
			appServers[index++] = receiver;
			VPNFactory factory = new SimVPNFactory(eventingSystem);
			factory.initialize(receiver);
			VPN vpn = factory.createVPN(s.getAddress());
			receiver.attachToServer(vpn);
			s.connectAppServer(receiverAddress);
			lastReceiverAddress = receiverAddress;
			// Create the TrafficReceiver's entry in the proxy lookup service
			topology.connectAppServer(receiverAddress, s.getAddress());
			receiverAddress = incrementAddress(receiverAddress);
		}

		// construct the app clients (TrafficGenerators) and connect them to Clients
		InetAddress firstTransmitterAddress = incrementAddress(lastReceiverAddress);
		InetAddress lastTransmitterAddress = null;
		InetAddress transmitterAddress = firstTransmitterAddress;
		for (ClientProxy proxy : clients) {
			VPNFactory factory = new SimVPNFactory(eventingSystem);
			TrafficGenerator t = new TrafficGenerator(transmitterAddress, proxy.getAddress(), factory, this,
					eventingSystem);
			proxy.connectAppClient(transmitterAddress);
			appClients.add(t);
			transmitterAddress = incrementAddress(transmitterAddress);
		}
		lastTransmitterAddress = transmitterAddress;

		// construct the statistics-gathering module

	}

	private InetAddress buildBaseAddress() {
		byte[] bytes = new byte[4];
		bytes[0] = 10;
		InetAddress result = null;
		try {
			result = InetAddress.getByAddress(bytes);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * Parse a property that specifies an integer.
	 * 
	 * @param propName the name of the property
	 * @return the value of the property
	 */
	public int getIntegerProperty(String propName) {
		return PropertyParser.getIntegerProperty(propName, properties);
	}

	public long getLongProperty(String propName) {
		return (long) PropertyParser.getIntegerProperty(propName, properties);
	}

	/**
	 * Parse a property that specifies an integer.
	 * 
	 * @param propName the name of the property
	 * @return the value of the property
	 */
	public float getFloatProperty(String propName) {
		return PropertyParser.getFloatProperty(propName, properties);
	}

	private static void addPropertiesIfNotAlreadyThere(Properties properties, Properties propertiesToAdd) {
		for (Object key : propertiesToAdd.keySet()) {
			String name = key.toString();
			if (!properties.containsKey(name)) {
				properties.put(name, propertiesToAdd.getProperty(name));
			}
		}
	}

	/**
	 * Run a simulation of the Socian Network Dynamics. Parameters are provided in
	 * the format "&lt;name&gt;=&lt;value&gt;". Order of precedence:
	 * <ol>
	 * <li>command line</li>
	 * <li>environment variables / System.properties</li>
	 * <li>properties in a "properties_file".</li>
	 * </ol>
	 * If the property "properties_file" is present, that file is opened and its
	 * properties are added, but only if that property was not already specified. If
	 * the file itself has a "properties_file" property, then the process is
	 * repeated for that file.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		Properties properties = new Properties();
		for (int i = 0; i < args.length; i++) {
			if (args[i].indexOf('=') < 0) {
				System.err.println("Argument #" + i + " (" + args[i] + ") is not in the format <name>=<value>.");
				System.exit(-1);
			}
			String name = args[i].substring(0, args[i].indexOf('='));
			String value = "";
			if (args[i].indexOf('=') < args[i].length() - 1) {
				value = args[i].substring(args[i].indexOf('=') + 1);
			}
			properties.put(name, value);
		}
		System.out.println("Properties = " + properties);
		Simulation sim = new Simulation(properties);
		sim.run();
		sim.saveResults();
	}

	public void saveResults() {
		// TODO Auto-generated method stub

	}

	public void run() {
		int numberOfThreads = getIntegerProperty("snd.number_of_threads");
		Thread[] threads = new Thread[numberOfThreads];
		for (int i = 0; i < threads.length; i++) {
			final int thdID = i;
			threads[i] = new Thread(null, eventingSystem, "Eventing System Thread " + thdID);
		}
		for (int i = 0; i < threads.length; i++) {
			threads[i].start();
		}
		for (int i = 0; i < threads.length; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				System.err.println(threads[i].toString() + " was interrupted: " + e);
			}
		}
		printEvent("The simulation has ended.");
	}
	
	public void printEvent(String msg) {
		System.out.println(String.format("%10.3f: %s", ((float)eventingSystem.getCurrentTime())/1000.0, msg));
	}

	public long getSeed() {
		return simRandom.nextLong();
	}

	public InetAddress chooseServer() {
		return appServers[simRandom.nextInt(appServers.length)].getAddress();
	}

	/**
	 * This is a bit of a hack. TrafficReceiver does not have access to the eventing
	 * system, but it does have the Simulation instance.
	 */
	public long getCurrentTime() {
		return eventingSystem.getCurrentTime();
	}

}
