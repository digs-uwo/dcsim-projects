package edu.uwo.csd.dcsim.projects.hierarchical.cnsm2014;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.common.*;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.capabilities.*;
import edu.uwo.csd.dcsim.management.policies.*;
import edu.uwo.csd.dcsim.projects.centralized.policies.ReactiveHostStatusPolicy;
import edu.uwo.csd.dcsim.projects.hierarchical.HierarchicalMetrics;
import edu.uwo.csd.dcsim.projects.hierarchical.VmFlavours;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.*;
import edu.uwo.csd.dcsim.projects.hierarchical.policies.*;

/**
 * This class serves to evaluate the Hierarchical Management System in the test 
 * environment for the CNSM 2014 paper. The strategy consists of the following 
 * policies:
 * 
 * + Level 3
 *   - AppPlacementPolicyLevel3
 *   - AppRelocationPolicyLevel3
 * + Level 2
 *   - AppPlacementPolicyLevel2
 *   - AppRelocationPolicyLevel2
 * + Level 1
 *   - AppPlacementPolicyLevel1
 *   - AppRelocationPolicyLevel1
 *   - AppConsolidationPolicyLevel1
 * 
 * The Placement policies run as needed, while the VM Relocation policies 
 * run reactively. The VM Consolidation policy runs periodically.
 * 
 * @author Gaston Keller
 *
 */
public class HierarchicalExperiment extends SimulationTask {

	private static Logger logger = Logger.getLogger(HierarchicalExperiment.class);
	
	private static final long[] randomSeeds = {6198910678692541341l,
		5646441053220106016l,
		-5705302823151233610l,
		8289672009575825404l,
		-4637549055860880177l,
		-4280782692131378509l,
		-1699811527182374894l,
		-6452776964812569334l,
		-7148920787255940546l,
		8311271444423629559l};
	private static final long N_SEEDS = 10;
	private static boolean printDefault = false;
	
	// Utilization thresholds. Default values.
	private static double LOWER = 0.60;
	private static double TARGET = 0.85;
	private static double UPPER = 0.90;
	
	private enum ServiceType {STATIC, DYNAMIC, RANDOM;}
	
	// Configurable parameters.
	private double lower;
	private double target;
	private double upper;
	private ServiceType serviceType;
	private int baseLoad;
	private int additionalLoad;
	private double changesPerDay;
	private long rampUpTime;
	private long startTime;
	private long duration;
	private Resources[] vmSizes;
	private int[] appTypes;
	
	
	public static void main(String args[]) {
		Simulation.initializeLogging();
		
		PrintStream printStream;
		try {
			printStream = new PrintStream("out_hierarchical");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		
		Resources[] vmSizes = {VmFlavours.manfi1(), VmFlavours.manfi2(), VmFlavours.manfi3()};
		int[] appTypes = {1, 2, 3, 4, 5};
		
		// Experiments: First Set.
		for (int expSet = 0; expSet < 4; expSet++) {
			
			// Generate VM sizes vector.
			switch (expSet) {
			case 0:
				vmSizes = new Resources[]{VmFlavours.manfi1()};
				break;
			case 1:
				vmSizes = new Resources[]{VmFlavours.manfi2()};
				break;
			case 2:
				vmSizes = new Resources[]{VmFlavours.manfi3()};
				break;
			case 3:
				vmSizes = new Resources[]{VmFlavours.manfi1(), VmFlavours.manfi2(), VmFlavours.manfi3()};
				break;
			}
			
			for (int i = 1; i <= 5; i++) {
				// Generate application types vector.
				appTypes = new int[i];
				for (int j = 0; j < appTypes.length; j++)
					appTypes[j] = j + 1;
				
				runSimulationSet(printStream, 1200, SimTime.hours(120), SimTime.days(13), SimTime.days(6), vmSizes, appTypes);
			}
		}
		
		// Experiments: Second Set.
		appTypes = new int[]{2};				// Create application types vector.
		while (appTypes[0] < 6) {
			runSimulationSet(printStream, 1200, SimTime.hours(120), SimTime.days(13), SimTime.days(6), vmSizes, appTypes);
			appTypes[0]++;
		}
		
		printStream.println("Done");
		printStream.close();
	}
	
	/**
	 * Static load experiment.
	 * 
	 * @param out
	 * @param baseLoad
	 * @param rampUpTime
	 * @param duration
	 * @param metricRecordStart
	 */
	private static void runSimulationSet(PrintStream out, 
			int baseLoad, 
			long rampUpTime, 
			long duration, 
			long metricRecordStart,
			Resources[] vmSizes,
			int[] appTypes) {
		
		runSimulationSet(out, ServiceType.STATIC, baseLoad, 0, 0, rampUpTime, 0, duration, metricRecordStart, LOWER, TARGET, UPPER, vmSizes, appTypes);
	}
	
	/**
	 * Random load experiment.
	 * 
	 * @param out
	 * @param baseLoad
	 * @param additionalLoad
	 * @param changesPerDay
	 * @param rampUpTime
	 * @param startTime
	 * @param duration
	 * @param metricRecordStart
	 */
	private static void runSimulationSet(PrintStream out, 
			int baseLoad, 
			int additionalLoad, 
			double changesPerDay, 
			long rampUpTime, 
			long startTime, 
			long duration, 
			long metricRecordStart,
			Resources[] vmSizes,
			int[] appTypes) {
		
		runSimulationSet(out, ServiceType.RANDOM, baseLoad, additionalLoad, changesPerDay, rampUpTime, startTime, duration, metricRecordStart, LOWER, TARGET, UPPER, vmSizes, appTypes);
	}
	
	private static void runSimulationSet(PrintStream out, 
			ServiceType serviceType, 
			int baseLoad, 
			int additionalLoad, 
			double changesPerDay, 
			long rampUpTime, 
			long startTime, 
			long duration, 
			long metricRecordStart, 
			double lower, 
			double target, 
			double upper,
			Resources[] vmSizes,
			int[] appTypes) {
		
		logger.info("Started New Simulation Set");
		logger.info(lower + "," + target + "," + upper + "," + 
				serviceType + "," + baseLoad + "," + additionalLoad + "," + changesPerDay + "," + 
				rampUpTime + "," + startTime + "," + duration + "," + metricRecordStart);
		logger.info("VM Sizes:");
		for (Resources vmSize : vmSizes) {
			logger.info("   " + vmSize.toString());
		}
		logger.info("Application Types: " + Arrays.toString(appTypes));
		
		List<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
		for (int i = 0; i < N_SEEDS; ++i)  {
			HierarchicalExperiment e = new HierarchicalExperiment("hierarchical-" + (i + 1), duration, metricRecordStart, randomSeeds[i]);
			e.setParameters(lower, target, upper, serviceType, baseLoad, additionalLoad, changesPerDay, rampUpTime, startTime, duration, vmSizes, appTypes);
			executor.addTask(e);
		}
		
		completedTasks = executor.execute(3);
		
		// Generate output.
		if (printDefault) {
			
			// Print output.
			for(SimulationTask task : completedTasks) {
				logger.info(task.getName());
				
				// Check if task has completed its execution (or actually failed).
				if (task.isComplete())
					task.getMetrics().printDefault(logger);
				else
					logger.info("Task did NOT complete its execution.");
			}
		}
		else {
			
			// Print output.
			for(SimulationTask task : completedTasks) {
				logger.info(task.getName());
				
				// Check if task has completed its execution (or actually failed).
				if (task.isComplete())
					task.getMetrics().printDefault(logger);
				else
					logger.info("Task did NOT complete its execution.");
			}
			
			// Output CSV file.
			out.println("Hierarchical Experiment");
			out.println("lower=" + lower + 
					" | target=" + target + 
					" | upper=" + upper + 
					" | serviceType=" + serviceType + 
					" | baseLoad=" + baseLoad + 
					" | additionalLoad=" + additionalLoad + 
					" | changesPerDay=" + changesPerDay + 
					" | rampUpTime=" + rampUpTime + 
					" | startTime=" + startTime + 
					" | duration=" + duration + 
					" | metricRecordStart=" + metricRecordStart);
			
			StringBuilder sb = new StringBuilder();
			sb.append("VmSizes: ");
			for (Resources vmSize : vmSizes) {
				sb.append(vmSize.toString() + " | ");
			}
			sb.append("| ApplicationTypes: " + Arrays.toString(appTypes));
			out.println(sb.toString());
			
			// Build dictionary of metrics to print.
			HashSet<String> metricSet = new HashSet<String>();
			for (SimulationTask task : completedTasks) {
				// Check if task has completed its execution (or actually failed).
				if (!task.isComplete())
					continue;
				
				for (Tuple<String, Object> metric : task.getMetrics().getMetricValues())
					metricSet.add(metric.a);
			}
			// Convert set to list.
			ArrayList<String> metricList = new ArrayList<String>();
			for (Iterator<String> iterator = metricSet.iterator(); iterator.hasNext();)
				metricList.add(iterator.next());
			Collections.sort(metricList);
			
			// Print heading.
			out.print("name");
			for (String metric : metricList) {
				out.printf(",%s", metric);
			}
			out.println("");
			
			// Print metrics.
			HashMap<String, Object> taskMetrics;
			for (SimulationTask task : completedTasks) {
				// Check if task has completed its execution (or actually failed).
				if (!task.isComplete()) {
					out.println(task.getName());
					continue;
				}
				
				taskMetrics = new HashMap<String, Object>();
				for (Tuple<String, Object> metric : task.getMetrics().getMetricValues())
					taskMetrics.put(metric.a, metric.b);
				
				out.print(task.getName());
				for (String metric : metricList) {
					if (taskMetrics.containsKey(metric))
						out.printf(",%s", taskMetrics.get(metric).toString());
					else
						out.print(",0");
				}
				out.println("");
			}
			
			out.println("");
			out.println("");
			out.flush();
		}
	}
	
	public HierarchicalExperiment(String name, long duration, long metricRecordStart) {
		super(name, duration);
		this.setMetricRecordStart(metricRecordStart);
	}
	
	public HierarchicalExperiment(String name, long duration, long metricRecordStart, long randomSeed) {
		super(name, duration);
		this.setMetricRecordStart(metricRecordStart);
		this.setRandomSeed(randomSeed);
	}
	
	private void setParameters(double lower, 
			double target, 
			double upper, 
			ServiceType serviceType, 
			int baseLoad, 
			int additionalLoad, 
			double changesPerDay, 
			long rampUpTime, 
			long startTime, 
			long duration,
			Resources[] vmSizes,
			int[] appTypes) {
		
		this.lower = lower;
		this.target = target;
		this.upper = upper;
		this.serviceType = serviceType;
		this.baseLoad = baseLoad;
		this.additionalLoad = additionalLoad;
		this.changesPerDay = changesPerDay;
		this.rampUpTime = rampUpTime;
		this.startTime = startTime;
		this.duration = duration;
		this.vmSizes = vmSizes;
		this.appTypes = appTypes;
	}
	
	/**
	 * Configures the simulation, creating the data centre and its management infrastructure, 
	 * setting parameters, and configuring the Services Producer.
	 */
	@Override
	public void setup(Simulation simulation) {
		
		// Register custom metrics.
		simulation.getSimulationMetrics().addCustomMetricCollection(new HierarchicalMetrics(simulation));
		
		// Create data centre.
		Cnsm2014TestEnvironment testEnv = new Cnsm2014TestEnvironment(simulation);
		DataCentre dc = testEnv.createInfrastructure(simulation);
		
		// Create management infrastructure.
		AutonomicManager dcManager = this.createMgmtInfrastructure(simulation, dc);
		
		// Set the VM sizes and application types (i.e., templates) available in this experiment.
		testEnv.setVmSizes(vmSizes);
		testEnv.setAppTypes(appTypes);
		
		// Create and start the Services Producer.
		switch (serviceType) {
			case STATIC:	testEnv.configureStaticServices(simulation, dcManager, baseLoad, rampUpTime, duration);
							break;
			case DYNAMIC:	//Cnsm2014TestEnvironment.configureDynamicServices(simulation, dcManager, legacyLoadGen);
							break;
			case RANDOM:	testEnv.configureRandomServices(simulation, dcManager, baseLoad, baseLoad + additionalLoad, changesPerDay, rampUpTime, startTime, duration);
							break;
		}
	}
	
	/**
	 * Creates the management infrastructure for the data centre, which includes creating 
	 * autonomic managers and setting their capabilities and policies.
	 */
	private AutonomicManager createMgmtInfrastructure(Simulation simulation, DataCentre dc) {
		
		// Create DC Manager.
		ClusterPoolManager clusterPool = new ClusterPoolManager();
		AutonomicManager dcManager = new AutonomicManager(simulation, clusterPool, new MigRequestRecord());
		
		// Install management policies in the autonomic manager.
		dcManager.installPolicy(new ClusterStatusPolicy(5));
		dcManager.installPolicy(new AppPlacementPolicyLevel3());
		dcManager.installPolicy(new RelocationPolicyLevel3());
		
		// TODO: Autonomic manager is NOT installed anywhere.
		
		for (Cluster cluster : dc.getClusters()) {
			
			// Create Cluster's autonomic manager.
			RackPoolManager rackPool = new RackPoolManager();
			AutonomicManager clusterManager = new AutonomicManager(simulation, new ClusterManager(cluster), rackPool, new MigRequestRecord());
			
			// Install management policies in the autonomic manager.
			clusterManager.installPolicy(new ClusterMonitoringPolicy(dcManager), SimTime.minutes(5), SimTime.minutes(simulation.getRandom().nextInt(5)));
			clusterManager.installPolicy(new RackStatusPolicy(5));
			clusterManager.installPolicy(new AppPlacementPolicyLevel2(dcManager));
			clusterManager.installPolicy(new RelocationPolicyLevel2(dcManager));
			
			// TODO: Autonomic manager is NOT installed anywhere.
			
			// Add Cluster and its autonomic manager to the capability of the hosting Data Centre.
			clusterPool.addCluster(cluster, clusterManager);
			
			for (Rack rack : cluster.getRacks()) {
				
				// Create Rack's autonomic manager.
				HostPoolManager hostPool = new HostPoolManager();
				AutonomicManager rackManager = new AutonomicManager(simulation, new RackManager(rack), new AppPoolManager(), hostPool, new VmPoolManager(), new MigRequestRecord(), new MigrationTrackingManager());
				
				// Install management policies in the autonomic manager.
				rackManager.installPolicy(new RackMonitoringPolicy(clusterManager), SimTime.minutes(5), SimTime.minutes(simulation.getRandom().nextInt(5)));
				rackManager.installPolicy(new ReactiveHostStatusPolicy(5));
				rackManager.installPolicy(new AppPoolPolicy());
				rackManager.installPolicy(new VmPoolPolicy());
				rackManager.installPolicy(new MigrationTrackingPolicy());
				rackManager.installPolicy(new AppPlacementPolicyLevel1(clusterManager, lower, upper, target));
				rackManager.installPolicy(new RelocationPolicyLevel1(clusterManager, lower, upper, target));
				rackManager.installPolicy(new ConsolidationPolicyLevel1(clusterManager, lower, upper, target), SimTime.hours(1), SimTime.hours(1));
				
				// TODO: Autonomic manager is NOT installed anywhere.
				
				// Add Rack and its autonomic manager to the capability of the hosting Cluster.
				rackPool.addRack(rack, rackManager);
				
				for (Host host : rack.getHosts()) {
					
					// Create Host's autonomic manager.
					AutonomicManager hostManager = new AutonomicManager(simulation, new HostManager(host));
					
					// Install management policies in the autonomic manager.
					hostManager.installPolicy(new HostMonitoringPolicy(rackManager), SimTime.minutes(2), SimTime.minutes(simulation.getRandom().nextInt(5)));
					hostManager.installPolicy(new HostOperationsPolicy(rackManager));
					
					// Install autonomic manager in the Host.
					host.installAutonomicManager(hostManager);
					
					// Add Host and its autonomic manager to the capability of the hosting Rack.
					hostPool.addHost(host, hostManager);
				}
			}
			
		}
		
		return dcManager;
	}

}
