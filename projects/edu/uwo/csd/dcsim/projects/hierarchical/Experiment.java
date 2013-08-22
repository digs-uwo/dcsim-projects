package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.Collection;

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
 * This class serves to evaluate the Periodic Hybrid Strategy in the test 
 * environment for the SVM 2013 paper. The strategy consists of the following 
 * policies:
 * 
 * + VmPlacementPolicyFFMHybrid
 * + VmRelocationPolicyFFIMDHybrid
 * + VmConsolidationPolicyFFDDIHybrid
 * 
 * The VM Placement policy runs as needed, while the VM Relocation and 
 * VM Consolidation policies run periodically.
 * 
 * @author Gaston Keller
 *
 */
public class Experiment extends SimulationTask {

	private static Logger logger = Logger.getLogger(Experiment.class);
	
	private double lower;			// Lower utilization threshold.
	private double upper;			// Upper utilization threshold.
	private double target;			// Target utilization threshold.
	
	/**
	 * Creates a new instance of Experiment. Sets experiment's duration to 10 days and 
	 * metrics recording start time at day 3.
	 */
	/**
	 * Creates a new instance of Experiment. Sets experiment's duration to 10 days and 
	 * metrics recording start time at day 3.
	 * 
	 * @param name			name of the simulation
	 * @param randomSeed	seed for random number generation
	 */
	public Experiment(String name, long randomSeed) {
		//super(name, SimTime.days(10));					// 10-day simulation
		super(name, SimTime.days(4));
		this.setMetricRecordStart(SimTime.days(2));		// start on 3rd day (i.e., after 2 days)
		this.setRandomSeed(randomSeed);
	}
	
	/**
	 * Configures the simulation, creating the data centre and its management infrastructure, 
	 * setting parameters, and configuring the Services Producer.
	 */
	@Override
	public void setup(Simulation simulation) {
		
		// Set utilization thresholds.
		lower = 0.60;
		upper = 0.90;
		target = 0.85;
		
		// Create data centre.
		DataCentre dc = HierarchicalTestEnvironment.createInfrastructure(simulation);
		
		// Create management infrastructure.
		AutonomicManager dcManager = this.createMgmtInfrastructure(simulation, dc);
		
		// Create and start the Services Producer.
		HierarchicalTestEnvironment.configureStaticServices(simulation, dcManager);
		//HierarchicalTestEnvironment.configureRandomServices(simulation, dcManager, 1, 6000, 16000);		// 10x #hosts & load
		//HierarchicalTestEnvironment.configureRandomServices(simulation, dcManager, 1, 600, 1600);
	}
	
	/**
	 * Creates the management infrastructure for the data centre, which includes creating 
	 * autonomic managers and setting their capabilities and policies.
	 */
	public AutonomicManager createMgmtInfrastructure(Simulation simulation, DataCentre dc) {
		
		// Create DC Manager.
		ClusterPoolManager clusterPool = new ClusterPoolManager();
		AutonomicManager dcManager = new AutonomicManager(simulation, clusterPool, new MigRequestRecord());
		
		// Install management policies in the autonomic manager.
		dcManager.installPolicy(new ClusterStatusPolicy(5));
		dcManager.installPolicy(new VmPlacementPolicyLevel2());
		dcManager.installPolicy(new VmRelocationPolicyLevel2());
		
		// TODO: Autonomic manager is NOT installed anywhere.
		
		for (Cluster cluster : dc.getClusters()) {
			
			// Create Cluster's autonomic manager.
			RackPoolManager rackPool = new RackPoolManager();
			AutonomicManager clusterManager = new AutonomicManager(simulation, new ClusterManager(cluster), rackPool, new MigRequestRecord());
			
			// Install management policies in the autonomic manager.
			clusterManager.installPolicy(new ClusterMonitoringPolicy(dcManager), SimTime.minutes(5), SimTime.minutes(simulation.getRandom().nextInt(5)));
			clusterManager.installPolicy(new RackStatusPolicy(5));
			clusterManager.installPolicy(new VmPlacementPolicyLevel1(dcManager));
			clusterManager.installPolicy(new VmRelocationPolicyLevel1(dcManager));
			
			// TODO: Autonomic manager is NOT installed anywhere.
			
			// Add Cluster and its autonomic manager to the capability of the hosting Data Centre.
			clusterPool.addCluster(cluster, clusterManager);
			
			for (Rack rack : cluster.getRacks()) {
				
				// Create Rack's autonomic manager.
				HostPoolManager hostPool = new HostPoolManager();
				AutonomicManager rackManager = new AutonomicManager(simulation, new RackManager(rack), hostPool, new MigRequestRecord());
				
				// Install management policies in the autonomic manager.
				rackManager.installPolicy(new RackMonitoringPolicy(clusterManager), SimTime.minutes(5), SimTime.minutes(simulation.getRandom().nextInt(5)));
				rackManager.installPolicy(new ReactiveHostStatusPolicy(5));
				rackManager.installPolicy(new VmPlacementPolicyFFMHybrid(clusterManager, lower, upper, target));
				rackManager.installPolicy(new VmRelocationPolicyFFIMHybrid(clusterManager, lower, upper, target));
				rackManager.installPolicy(new VmConsolidationPolicyFFDDIHybrid(clusterManager, lower, upper, target), SimTime.hours(1), SimTime.hours(1));
				
				// TODO: Autonomic manager is NOT installed anywhere.
				
				// Add Rack and its autonomic manager to the capability of the hosting Cluster.
				rackPool.addRack(rack, rackManager);
				
				for (Host host : rack.getHosts()) {
					
					// Create Host's autonomic manager.
					AutonomicManager hostManager = new AutonomicManager(simulation, new HostManager(host));
					
					// Install management policies in the autonomic manager.
					hostManager.installPolicy(new HostMonitoringPolicy(rackManager), SimTime.minutes(2), SimTime.minutes(simulation.getRandom().nextInt(5)));
					hostManager.installPolicy(new HostOperationsPolicy());
					
					// Install autonomic manager in the Host.
					host.installAutonomicManager(hostManager);
					
					// Add Host and its autonomic manager to the capability of the hosting Rack.
					hostPool.addHost(host, hostManager);
				}
			}
			
		}
		
		return dcManager;
	}
	
	public static void main(String args[]) {
		
		Simulation.initializeLogging();
		
		Collection<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
		
		executor.addTask(new Experiment("hierarchical-1", 6198910678692541341l));
//		executor.addTask(new Experiment("hierarchical-2", 5646441053220106016l));
//		executor.addTask(new Experiment("hierarchical-3", -5705302823151233610l));
//		executor.addTask(new Experiment("hierarchical-4", 8289672009575825404l));
//		executor.addTask(new Experiment("hierarchical-5", -4637549055860880177l));
		
		completedTasks = executor.execute();
		
		for(SimulationTask task : completedTasks) {
			logger.info(task.getName());
			HierarchicalTestEnvironment.printMetrics(task.getResults());
			
			SimulationTraceWriter traceWriter = new SimulationTraceWriter(task);
			traceWriter.writeTrace();
		}
	}

}
