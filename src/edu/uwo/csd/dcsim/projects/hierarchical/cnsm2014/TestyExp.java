package edu.uwo.csd.dcsim.projects.hierarchical.cnsm2014;

import java.io.FileNotFoundException;
import java.io.PrintStream;
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
public class TestyExp extends SimulationTask {

	private static Logger logger = Logger.getLogger(TestyExp.class);
	
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
	private static final long N_SEEDS = 1;
	private static boolean printDefault = true;
	
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
	
	
	public static void main(String args[]) {
		Simulation.initializeLogging();
		
		PrintStream printStream;
		try {
			printStream = new PrintStream("out_hierarchical");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		
		// Static load experiments.
//		runSimulationSet(printStream, 400, SimTime.hours(40), SimTime.days(10), SimTime.days(2));
//		runSimulationSet(printStream, 800, SimTime.hours(80), SimTime.days(12), SimTime.days(4));
		runSimulationSet(printStream, 1440, SimTime.hours(144), SimTime.days(14), SimTime.days(6));
//		runSimulationSet(printStream, 1600, SimTime.hours(160), SimTime.days(15), SimTime.days(7));
		
		// Random load experiments.
//		runSimulationSet(printStream, 800, 800, 1, SimTime.hours(80), SimTime.days(4), SimTime.days(12), SimTime.days(4));
		
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
			long metricRecordStart) {
		
		runSimulationSet(out, ServiceType.STATIC, baseLoad, 0, 0, rampUpTime, 0, duration, metricRecordStart, LOWER, TARGET, UPPER);
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
			long metricRecordStart) {
		
		runSimulationSet(out, ServiceType.RANDOM, baseLoad, additionalLoad, changesPerDay, rampUpTime, startTime, duration, metricRecordStart, LOWER, TARGET, UPPER);
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
			double upper) {
		
		logger.info("Started New Simulation Set");
		logger.info(lower + "," + target + "," + upper + "," + 
				serviceType + "," + baseLoad + "," + additionalLoad + "," + changesPerDay + "," + 
				rampUpTime + "," + startTime + "," + duration + "," + metricRecordStart);
		
		List<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
		for (int i = 0; i < N_SEEDS; ++i)  {
			TestyExp e = new TestyExp("hierarchical-" + (i + 1), duration, metricRecordStart, randomSeeds[i]);
			e.setParameters(lower, target, upper, serviceType, baseLoad, additionalLoad, changesPerDay, rampUpTime, startTime, duration);
			executor.addTask(e);
		}
		
		completedTasks = executor.execute(4);
		
		// Generate output.
		if (printDefault) {
			
			// Print output.
			for(SimulationTask task : completedTasks) {
				logger.info(task.getName());
				task.getMetrics().printDefault(logger);
			}
		}
		else {
			
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
			
			for(SimulationTask task : completedTasks) {
				if (completedTasks.indexOf(task) == 0) {
					task.getMetrics().printCSV(out);
				} else {
					task.getMetrics().printCSV(out, false);
				}
			}
			
			out.println("");
			out.println("");
			out.flush();
		}
	}
	
	public TestyExp(String name, long duration, long metricRecordStart) {
		super(name, duration);
		this.setMetricRecordStart(metricRecordStart);
	}
	
	public TestyExp(String name, long duration, long metricRecordStart, long randomSeed) {
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
			long duration) {
		
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
	}
	
	/**
	 * Configures the simulation, creating the data centre and its management infrastructure, 
	 * setting parameters, and configuring the Services Producer.
	 */
	@Override
	public void setup(Simulation simulation) {
		
		// Create data centre.
		Testy testEnv = new Testy(simulation);
		DataCentre dc = testEnv.createInfrastructure(simulation);
		
		// Create management infrastructure.
		AutonomicManager dcManager = this.createMgmtInfrastructure(simulation, dc);
		
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
		dcManager.installPolicy(new AppRelocationPolicyLevel3());
		
		// TODO: Autonomic manager is NOT installed anywhere.
		
		for (Cluster cluster : dc.getClusters()) {
			
			// Create Cluster's autonomic manager.
			RackPoolManager rackPool = new RackPoolManager();
			AutonomicManager clusterManager = new AutonomicManager(simulation, new ClusterManager(cluster), rackPool, new MigRequestRecord());
			
			// Install management policies in the autonomic manager.
			clusterManager.installPolicy(new ClusterMonitoringPolicy(dcManager), SimTime.minutes(5), SimTime.minutes(simulation.getRandom().nextInt(5)));
			clusterManager.installPolicy(new RackStatusPolicy(5));
			clusterManager.installPolicy(new AppPlacementPolicyLevel2(dcManager));
			clusterManager.installPolicy(new AppRelocationPolicyLevel2(dcManager));
			
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
				rackManager.installPolicy(new AppRelocationPolicyLevel1(clusterManager, lower, upper, target));
				rackManager.installPolicy(new AppConsolidationPolicyLevel1(clusterManager, lower, upper, target), SimTime.hours(1), SimTime.hours(1));
				
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
