package com.github.glfrazier.snd.simulation;

import static com.github.glfrazier.snd.node.Node.TRANSMISSION_LATENCY;
import static com.github.glfrazier.snd.util.AddressUtils.incrementAddress;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
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
import com.github.glfrazier.event.util.Synchronizer;
import com.github.glfrazier.snd.node.Node;
import com.github.glfrazier.snd.node.ProxyNode;
import com.github.glfrazier.snd.util.AddressUtils.AddressPair;
import com.github.glfrazier.snd.util.PropertyParser;

public class Simulation {

	private static final String DEFAULT_BASE_ADDRESS = "10.0.0.0";
	private EventingSystem eventingSystem;
	private Properties properties;
	private List<ProxyNode> clients = new ArrayList<>();
	private List<ProxyNode> servers = new ArrayList<>();
	private TrafficGenerator[] appClients;
	private TrafficReceiver[] appServers;
	private Random simRandom;
	private Statistics stats;
	private float attackProb;
	private long endTime;
	private long warmupTime;
	private TrafficReceiver[] victims;
	public boolean verbose;
	/**
	 * A list of message IDs that will trigger verbose processing. Useful for
	 * debugging weird behavior in an individual message.
	 */
	private Set<Long> verboseMessages;
	/** The map whereby VPN endpoints find each other. See SimVPN. */
	private final Map<AddressPair, SimVPN> vpnMap = Collections.synchronizedMap(new HashMap<>());
	/**
	 * True if we want the TrafficGenerator instances to keep track of which
	 * messages are not responded to.
	 */
	private boolean recordOutstandingMessages;
	private List<Node> introducers;

	public Simulation(Properties properties) throws Exception {
		this.properties = properties;

		if (getBooleanProperty("snd.sim.verbose", false)) {
			verbose = true;
			// eventingSystem.setVerbose(true);
			properties.setProperty("snd.node.verbose", "true");
		}
		simRandom = new Random();
		if (!properties.containsKey("snd.sim.seed")) {
			long seed = System.currentTimeMillis();
			properties.setProperty("snd.sim.seed", Long.toString(seed));
		}
		simRandom.setSeed(getLongProperty("snd.sim.seed"));
		attackProb = getProbabilityProperty("snd.sim.attack_probability");
		verboseMessages = new HashSet<Long>();
		if (properties.containsKey("snd.sim.verbose_msg_IDs")) {
			String[] msgIDs = getListProperty("snd.sim.verbose_msg_IDs");
			for (String id : msgIDs) {
				verboseMessages.add(Long.parseLong(id));
			}
		}
		recordOutstandingMessages = getBooleanProperty("snd.sim.outstanding_messages", false);

		// Construct the eventing system. Since this is a simulation, we are *NOT*
		// running the EventingSystem in realtime.
		eventingSystem = new EventingSystem("EventingSystem", EventingSystem.NOT_REALTIME);
		eventingSystem.setVerbose(verbose);
		stats = new Statistics(properties);
		endTime = getLongProperty("snd.sim.end_time");
		warmupTime = getLongProperty("snd.sim.warmup_time");
		eventingSystem.setEndTime(endTime);
		if (warmupTime > 0) {
			eventingSystem.scheduleEventRelative(new EventProcessor() {

				@Override
				public void process(Event e, EventingSystem eventingSystem, long t) {
					stats.zeroize();
				}
			}, Event.EVENT, warmupTime);
		}

		System.out.println("Properties parsed; building the network.");

		// build the introducer network
		Map<InetAddress, Node> introducerMap = new HashMap<>();
		ButterflyNetwork topology = null;
		int rowsOfIntroducers = getIntegerProperty("snd.sim.number_of_introducer_rows");
		int colsOfIntroducers = getIntegerProperty("snd.sim.number_of_introducer_cols");
		if (true) {
			int fanout = getIntegerProperty("snd.sim.introducer_fanout");
			InetAddress baseAddress = InetAddress.getByName(DEFAULT_BASE_ADDRESS);
			baseAddress = getIPAddressProperty("snd.sim.network_base_address", baseAddress.toString().substring(1));
			topology = new ButterflyNetwork(fanout, rowsOfIntroducers, colsOfIntroducers, baseAddress);
			introducers = new ArrayList<>(rowsOfIntroducers * colsOfIntroducers);
			for (int row = 0; row < rowsOfIntroducers; row++) {
				for (int col = 0; col < colsOfIntroducers; col++) {
					InetAddress address = topology.getAddressOfElement(row, col);
					SimImpl impl = new SimImpl(this, topology);
					Node introducer = new Node(address, impl, eventingSystem, properties);
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
					Node ii = introducers.get(i);
					Node ij = introducers.get(j);
					if (topology.areConnected(ii.getAddress(), ij.getAddress())) {
						try {
							Object keyingMaterial = ii.generateKeyingMaterial();
							ii.createVPN(ij.getAddress(), keyingMaterial);
							ij.createVPN(ii.getAddress(), keyingMaterial);
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
				SimImpl impl = new SimImpl(this, topology);
				ProxyNode serverProxy = new ProxyNode(serverAddress, impl, eventingSystem, properties, stats);
				impl.setNode(serverProxy);
				servers.add(serverProxy);
				InetAddress introAddr = topology.getAddressOfElement(index, colsOfIntroducers - 1);
				Node introImpl = introducerMap.get(introAddr);
				try {
					Object keyingMaterial = serverProxy.generateKeyingMaterial();
					serverProxy.connectInitialIntroducer(introAddr, keyingMaterial);
					introImpl.createVPN(serverAddress, keyingMaterial);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(-1);
				}
				topology.connectProxy(serverAddress, introAddr);
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
				SimImpl impl = new SimImpl(this, topology);
				ProxyNode clientProxy = new ProxyNode(clientAddress, impl, eventingSystem, properties, stats);
				impl.setNode(clientProxy);
				clients.add(clientProxy);
				InetAddress introAddr = topology.getAddressOfElement(index, 0);
				Node introImpl = introducerMap.get(introAddr);
				try {
					Object keyingMaterial = clientProxy.generateKeyingMaterial();
					clientProxy.connectInitialIntroducer(introAddr, keyingMaterial);
					introImpl.createVPN(clientAddress, keyingMaterial);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(-1);
				}
				topology.connectProxy(clientAddress, introAddr);
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
		for (ProxyNode proxy : servers) {
			TrafficReceiver receiver = new TrafficReceiver(receiverAddress, falsePositive, falseNegative, this);
			SimVPNManager factory = new SimVPNManager(this, eventingSystem, receiver);
			appServers[index++] = receiver;
			factory.createVPN(proxy.getAddress(), null);
			receiver.attachToServer(getVpnMap().get(new AddressPair(receiver.getAddress(), proxy.getAddress())));
			proxy.connectProxiedHost(receiverAddress, null);
			lastReceiverAddress = receiverAddress;
			// Create the TrafficReceiver's entry in the proxy lookup service
			topology.setProxyFor(receiverAddress, proxy.getAddress());
			receiverAddress = incrementAddress(receiverAddress);
			if (verbose) {
				System.out.println("Constructed " + receiver + " and connected it to " + proxy);
			}
		}

		// construct the app clients (TrafficGenerators) and connect them to Clients
		InetAddress firstGeneratorAddress = incrementAddress(lastReceiverAddress);
		firstGeneratorAddress = getIPAddressProperty("snd.sim.first_appclient_address",
				firstGeneratorAddress.toString().substring(1));
		InetAddress lastGeneratorAddress = null;
		InetAddress generatorAddress = firstGeneratorAddress;
		appClients = new TrafficGenerator[clients.size()];
		index = 0;
		for (ProxyNode proxy : clients) {
			TrafficGenerator generator = new TrafficGenerator(generatorAddress, this, eventingSystem);
			SimVPNManager factory = new SimVPNManager(this, eventingSystem, generator);
			factory.createVPN(proxy.getAddress(), null);
			generator.attachToProxy(this.getVpnMap().get(new AddressPair(generator.getAddress(), proxy.getAddress())));
			proxy.connectProxiedHost(generatorAddress, null);
			// Create the TrafficGenerator's entry in the proxy lookup service
			topology.setProxyFor(generatorAddress, proxy.getAddress());
			appClients[index++] = generator;
			generatorAddress = incrementAddress(generatorAddress);
			if (verbose) {
				System.out.println("Constructed " + generator + " and connected it to " + proxy);
			}
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

		System.out.println("Network construction completed.");
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
		return PropertyParser.getLongProperty(propName, properties);
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

	@SuppressWarnings("unused")
	private InetAddress getIPAddressProperty(String propName) {
		return PropertyParser.getIPAddressProperty(propName, properties);
	}

	public float getProbabilityProperty(String propName) {
		return PropertyParser.getProbabilityProperty(propName, properties);
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
		Properties properties = PropertyParser.parseCmdLine(args, "snd");
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

	private void addTimeReporter() {

		final long SIXTY_SECONDS = 60 * 1000;
		final long FIVE_MINUTES = 5 * SIXTY_SECONDS;

		EventProcessor timeReporter = new EventProcessor() {

			@Override
			public void process(Event e, EventingSystem eventingSystem, long t) {
				printEvent(this + ": Time has passed. Running the GC");
				Runtime.getRuntime().gc();
				eventingSystem.scheduleEventRelative(this, e, FIVE_MINUTES);
			}

			@Override
			public String toString() {
				return "Time Reporter";
			}

		};
		eventingSystem.scheduleEvent(timeReporter, new Event() {
		});
	}

	public void run() {
		addTimeReporter();
		stats.startSimulation();
		int numberOfThreads = getIntegerProperty("snd.sim.number_of_threads");
		Thread[] threads = new Thread[numberOfThreads];
		for (int i = 0; i < threads.length; i++) {
			final int thdID = i;
			threads[i] = new Thread(null, eventingSystem, "Eventing System Thread " + thdID);
		}
		new Synchronizer(threads, TRANSMISSION_LATENCY - 3, //
				eventingSystem);
		System.out.println("Starting the threads. The simulation will end at time " + endTime);
		System.out.println("===========================================");
		for (int i = 0; i < threads.length; i++) {
			threads[i].start();
		}
		Thread t = new Thread("DEBUG") {
			private long eventsDelivered;

			public void run() {
				while(true) {
					try {
						Thread.sleep(30000);
					} catch (InterruptedException e) {
						return;
					}
					for(int i=0; i<threads.length; i++) {
						StackTraceElement[] x = threads[i].getStackTrace();
						System.out.println(threads[i].getName());
						for(int j=0; j<x.length; j++) {
							System.out.println("\t" + x[j].getFileName() + " @ " + x[j].getLineNumber());
						}
					}
					long irSize = 0;
					for(Node n : introducers) {
						irSize += n.getPendingFeedbacksSize();
					}
					System.out.println("Free Memory = " + Runtime.getRuntime().freeMemory());
					System.out.println("Total Memory = " + Runtime.getRuntime().totalMemory());
					System.out.println("vpnMap.size() = " + vpnMap.size());
					long ted = eventingSystem.getTotalEventsDelivered();
					System.out.println("#events processed = " + ted);
					System.out.println("events in this period = " + (ted - eventsDelivered));
					System.out.println("Pending Feedbacks = " + irSize);
					eventsDelivered = ted;
					System.out.println("===============================");
				}
			}
		};
		t.setDaemon(true);
		t.start();
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
		if (recordOutstandingMessages) {
			System.out.println("Examining messages lost in transit:");
			for (TrafficGenerator tg : appClients) {
				tg.printOutstandingMessages();
			}
		}
		System.out.println("============");
	}

	public void printEvent(String msg) {
		System.out.println(String.format("%10.3f: %s", ((float) eventingSystem.getCurrentTime()) / 1000.0, msg));
	}

	public String addTimePrefix(String msg) {
		return String.format("%10.3f: %s", ((float) eventingSystem.getCurrentTime()) / 1000.0, msg);
	}

	public long getSeed() {
		return simRandom.nextLong();
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
		if (sender.isAttacker() && !inWarmup()) {
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

	public boolean isVerboseMessage(long id) {
		return verboseMessages.contains(id);
	}

	public Map<AddressPair, SimVPN> getVpnMap() {
		return vpnMap;
	}

	public boolean recordOutstandingMessages() {
		return recordOutstandingMessages;
	}

	private boolean inWarmup() {
		return eventingSystem.getCurrentTime() < warmupTime;
	}

	public Properties getProperties() {
		return properties;
	}

}
