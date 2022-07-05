package com.github.glfrazier.snd.simulation;

import static com.github.glfrazier.snd.util.AddressUtils.incrementAddress;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import com.github.glfrazier.event.EventingSystem;
import com.github.glfrazier.snd.node.ClientProxy;
import com.github.glfrazier.snd.node.SNDNode;
import com.github.glfrazier.snd.node.ServerProxy;
import com.github.glfrazier.snd.util.PropertyParser;

public class Simulation {

	private static final String DEFAULT_BASE_ADDRESS = "10.0.0.0";
	private EventingSystem eventingSystem;
	private Properties properties;
	private List<ClientProxy> clients = new ArrayList<>();
	private List<ServerProxy> servers = new ArrayList<>();
	private TrafficGenerator[] appClients;
	private TrafficReceiver[] appServers;
	private Random simRandom;
	private Statistics stats;
	private float attackProb;
	private long endTime;
	private TrafficReceiver[] victims;
	public boolean verbose;

	public Simulation(Properties properties) throws Exception {
		this.properties = properties;

		if (getBooleanProperty("snd.sim.verbose", false)) {
			verbose = true;
			// eventingSystem.setVerbose(true);
			properties.setProperty("snd.node.verbose", "true");
		}
		
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

		attackProb = getProbabilityProperty("snd.sim.attack_probability");

		// Construct the eventing system. Since this is a simulation, we are *NOT*
		// running the EventingSystem in realtime.
		eventingSystem = new EventingSystem(EventingSystem.NOT_REALTIME);
		stats = new Statistics(properties);
		endTime = getLongProperty("snd.sim.end_time");
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
			InetAddress baseAddress = InetAddress.getByName(DEFAULT_BASE_ADDRESS);
			baseAddress = getIPAddressProperty("snd.sim.network_base_address", baseAddress.toString().substring(1));
			topology = new ButterflyNetwork(fanout, rowsOfIntroducers, colsOfIntroducers, baseAddress);
			List<SNDNode> introducers = new ArrayList<>(rowsOfIntroducers * colsOfIntroducers);
			for (int row = 0; row < rowsOfIntroducers; row++) {
				for (int col = 0; col < colsOfIntroducers; col++) {
					InetAddress address = topology.getAddressOfElement(row, col);
					SimImpl impl = new SimImpl(topology);
					SNDNode introducer = new SNDNode(address, impl, eventingSystem, properties);
					impl.setNode(introducer);
					introducers.add(introducer);
					introducerMap.put(address, introducer);
					if (verbose) {
						System.out.println("Constructed " + introducer);
					}
				}
			}
			for (int i = 0; i < introducers.size() - 1; i++) {
				for (int j = i + 1; j < introducers.size(); j++) {
					SNDNode ii = introducers.get(i);
					SNDNode ij = introducers.get(j);
					if (topology.areConnected(ii.getAddress(), ij.getAddress())) {
						try {
							ii.getImplementation().getComms().openVPN(ij.getAddress(), null);
							ij.getImplementation().getComms().openVPN(ii.getAddress(), null);
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
		firstServerAddress = getIPAddressProperty("snd.sim.first_server_address",
				firstServerAddress.toString().substring(1));
		InetAddress lastServerAddress = null;
		int numberOfServers = getIntegerProperty("snd.sim.number_of_servers");
		if (true) {
			InetAddress serverAddress = firstServerAddress;
			int index = 0;
			for (int i = 0; i < numberOfServers; i++) {
				SimImpl impl = new SimImpl(topology);
				ServerProxy serverProxy = new ServerProxy(serverAddress, impl, eventingSystem, properties);
				impl.setNode(serverProxy);
				servers.add(serverProxy);
				InetAddress introAddr = topology.getAddressOfElement(index, colsOfIntroducers - 1);
				SNDNode introImpl = introducerMap.get(introAddr);
				try {
					serverProxy.connectFinalIntroducer(introAddr);
					introImpl.getImplementation().getComms().openVPN(serverAddress, null);
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
				if (verbose) {
					System.out.println("Constructed " + serverProxy + " and connected it to " + introAddr);
				}
			}
		}

		// construct client proxies and link them to appropriate introducers
		int numberOfClients = getIntegerProperty("snd.sim.number_of_clients");
		InetAddress firstClientAddress = incrementAddress(lastServerAddress);
		firstClientAddress = getIPAddressProperty("snd.sim.first_client_address",
				firstClientAddress.toString().substring(1));
		InetAddress lastClientAddress = null;
		if (true) {
			InetAddress clientAddress = firstClientAddress;
			int index = 0;
			for (int i = 0; i < numberOfClients; i++) {
				SimImpl impl = new SimImpl(topology);
				ClientProxy clientProxy = new ClientProxy(clientAddress, impl, eventingSystem, properties, stats);
				impl.setNode(clientProxy);
				clients.add(clientProxy);
				InetAddress introAddr = topology.getAddressOfElement(index, 0);
				SNDNode introImpl = introducerMap.get(introAddr);
				try {
					clientProxy.connectInitialIntroducer(introAddr);
					introImpl.getImplementation().getComms().openVPN(clientAddress, null);
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
				if (verbose) {
					System.out.println("Constructed " + clientProxy + " and connected it to " + introAddr);
				}
			}
		}

		// construct app servers (TrafficReceivers) and connect them to ServerProxies
		InetAddress firstReceiverAddress = incrementAddress(lastClientAddress);
		firstReceiverAddress = getIPAddressProperty("snd.sim.first_appserver_address",
				firstReceiverAddress.toString().substring(1));
		InetAddress lastReceiverAddress = null;
		float falsePositive = getFloatProperty("snd.sim.sensorFP");
		float falseNegative = getFloatProperty("snd.sim.sensorFN");
		InetAddress receiverAddress = firstReceiverAddress;
		appServers = new TrafficReceiver[servers.size()];
		int index = 0;
		for (ServerProxy s : servers) {
			TrafficReceiver receiver = new TrafficReceiver(receiverAddress, falsePositive, falseNegative, this);
			appServers[index++] = receiver;
			SimVPNFactory factory = new SimVPNFactory(eventingSystem);
			factory.initialize(receiver);
			SimVPNImpl vpn = (SimVPNImpl) factory.createVPN(s.getAddress(), null);
			receiver.attachToServer(vpn);
			s.connectAppServer(receiverAddress);
			lastReceiverAddress = receiverAddress;
			// Create the TrafficReceiver's entry in the proxy lookup service
			topology.connectAppServer(receiverAddress, s.getAddress());
			receiverAddress = incrementAddress(receiverAddress);
		}

		// construct the app clients (TrafficGenerators) and connect them to Clients
		InetAddress firstGeneratorAddress = incrementAddress(lastReceiverAddress);
		firstGeneratorAddress = getIPAddressProperty("snd.sim.first_appclient_address",
				firstGeneratorAddress.toString().substring(1));
		InetAddress lastGeneratorAddress = null;
		InetAddress generatorAddress = firstGeneratorAddress;
		appClients = new TrafficGenerator[clients.size()];
		index = 0;
		for (ClientProxy proxy : clients) {
			TrafficGenerator generator = new TrafficGenerator(generatorAddress, this, eventingSystem);
			SimVPNFactory factory = new SimVPNFactory(eventingSystem);
			factory.initialize(generator);
			SimVPNImpl vpn = (SimVPNImpl) factory.createVPN(proxy.getAddress(), null);
			generator.attachToProxy(vpn);
			proxy.connectAppClient(generatorAddress);
			appClients[index++] = generator;
			generatorAddress = incrementAddress(generatorAddress);
		}
		lastGeneratorAddress = generatorAddress;

		// Assign attackers and victims
		int numberOfAttackers = getIntegerProperty("snd.sim.number_of_attackers");
		if (numberOfAttackers > appClients.length) {
			throw new IllegalArgumentException(
					"There are more attackers (" + numberOfAttackers + ") than clients (" + appClients.length + ")");
		}
		if (true) {
			List<TrafficGenerator> candidates = new ArrayList<>(appClients.length);
			for (TrafficGenerator tg : appClients) {
				candidates.add(tg);
			}
			for (int i = 0; i < numberOfAttackers; i++) {
				TrafficGenerator attacker = candidates.remove(simRandom.nextInt(candidates.size()));
				attacker.setAttacker();
			}
		}

		// Assign attackers and victims
		int numberOfVictims = getIntegerProperty("snd.sim.number_of_victims", servers.size());
		if (numberOfVictims > servers.size()) {
			throw new IllegalArgumentException(
					"There are more victims (" + numberOfVictims + ") than servers (" + servers.size() + ")");
		}
		if (true) {
			List<TrafficReceiver> candidates = new ArrayList<>(servers.size());
			for (TrafficReceiver tr : appServers) {
				candidates.add(tr);
			}
			List<TrafficReceiver> ltr = new ArrayList<TrafficReceiver>();
			for (int i = 0; i < numberOfVictims; i++) {
				TrafficReceiver victim = candidates.remove(simRandom.nextInt(candidates.size()));
				ltr.add(victim);
			}
			victims = (TrafficReceiver[]) ltr.toArray(new TrafficReceiver[0]);
		}
		// construct the statistics-gathering module

	}

	private boolean getBooleanProperty(String propName, boolean defaultValue) {
		return PropertyParser.getBooleanProperty(propName, defaultValue, properties);
	}

	private int getIntegerProperty(String propName, int defaultValue) {
		return PropertyParser.getIntegerProperty(propName, defaultValue, properties);
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

	public String[] getListProperty(String propName) {
		return PropertyParser.getListProperty(propName, properties);
	}

	public String[] getListProperty(String propName, String defaultValue) {
		return PropertyParser.getListProperty(propName, defaultValue, properties);
	}

	private InetAddress getIPAddressProperty(String propName, String defaultValue) {
		return PropertyParser.getIPAddressProperty(propName, defaultValue, properties);
	}

	private InetAddress getIPAddressProperty(String propName) {
		return PropertyParser.getIPAddressProperty(propName, properties);
	}

	public float getProbabilityProperty(String propName) {
		return PropertyParser.getProbabilityProperty(propName, properties);
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
		Simulation sim = null;
		try {
			sim = new Simulation(properties);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		sim.run();
		sim.saveResults();
	}

	public void saveResults() {
		// TODO Auto-generated method stub

	}

	public void run() {
		stats.startSimulation();
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
		long eventsProcessed = eventingSystem.getTotalEventsDelivered();
		properties.setProperty("events_processed", Long.toString(eventsProcessed));
		System.out.println("events_processed = " + eventsProcessed);
		try {
			stats.save(properties);
		} catch (IOException e) {
			System.err.println("Failed to save simulation results:");
			e.printStackTrace();
		}
	}

	public void printEvent(String msg) {
		System.out.println(String.format("%10.3f: %s", ((float) eventingSystem.getCurrentTime()) / 1000.0, msg));
	}

	public long getSeed() {
		return simRandom.nextLong();
	}

	private InetAddress chooseServer() {
		return appServers[simRandom.nextInt(appServers.length)].getAddress();
	}

	/**
	 * This is a bit of a hack. TrafficReceiver does not have access to the eventing
	 * system, but it does have the Simulation instance.
	 */
	public long getCurrentTime() {
		return eventingSystem.getCurrentTime();
	}

	public Statistics getStats() {
		return stats;
	}

	public static class MessageMetaData {

		public final InetAddress destination;
		public final boolean isAttack;

		public MessageMetaData(InetAddress destination, boolean isAttack) {
			this.destination = destination;
			this.isAttack = isAttack;
		}

	}

	public MessageMetaData getNextMessageToSend(TrafficGenerator sender) {
		InetAddress destination = null;
		boolean isAttack = false;
		if (sender.isAttacker()) {
			isAttack = simRandom.nextFloat() < attackProb;
		}
		if (isAttack) {
			destination = victims[simRandom.nextInt(victims.length)].getAddress();
		} else {
			destination = appServers[simRandom.nextInt(appServers.length)].getAddress();
		}
		return new MessageMetaData(destination, isAttack);
	}

	public long getEndTime() {
		return endTime;
	}

}
