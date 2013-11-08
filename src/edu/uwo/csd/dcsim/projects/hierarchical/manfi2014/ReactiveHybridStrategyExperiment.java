package edu.uwo.csd.dcsim.projects.hierarchical.manfi2014;

import java.util.Collection;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.common.*;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.capabilities.*;
import edu.uwo.csd.dcsim.management.policies.*;
import edu.uwo.csd.dcsim.projects.centralized.policies.*;

/**
 * This class serves to evaluate the (centralized) Reactive Hybrid Strategy 
 * in the test environment for the ManFI 2014 paper. The strategy consists of 
 * the following policies:
 * 
 * + VmPlacementPolicyFFMHybrid
 * + VmRelocationPolicyHybridReactive
 * + VmConsolidationPolicyFFDDIHybrid
 * 
 * The VM Placement policy runs as needed, while the VM Consolidation policy 
 * runs periodically. The VM Relocation policy is triggered with every Host 
 * Status Update: a Stress Check is performed on the Host and if the check is 
 * positive, the VM Relocation process is started.
 * 
 * @author Gaston Keller
 *
 */
public class ReactiveHybridStrategyExperiment extends SimulationTask {

	private static Logger logger = Logger.getLogger(ReactiveHybridStrategyExperiment.class);
	
	private double lower;			// Lower utilization threshold.
	private double upper;			// Upper utilization threshold.
	private double target;			// Target utilization threshold.
	
	/**
	 * Creates a new instance of ReactiveHybridStrategyExperiment. Sets experiment's 
	 * duration to 10 days and metrics recording start time at day 3.
	 * 
	 * @param name			name of the simulation
	 * @param randomSeed	seed for random number generation
	 */
	public ReactiveHybridStrategyExperiment(String name, long randomSeed) {
		super(name, SimTime.days(10));					// 10-day simulation
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
		DataCentre dc = ManFI2014TestEnvironment.createInfrastructure(simulation);
		
		// Create management infrastructure.
		AutonomicManager dcManager = this.createMgmtInfrastructure(simulation, dc);
		
		// Create and start the Services Producer.
		ManFI2014TestEnvironment.configureStaticServices(simulation, dcManager, true);
		//ManFI2014TestEnvironment.configureRandomServices(simulation, dcManager, 1, 600, 1600, true);
		//ManFI2014TestEnvironment.configureRandomServices(simulation, dcManager, 1, 6000, 16000, true);		// 10x #hosts & load
	}
	
	/**
	 * Creates the management infrastructure for the data centre, which includes creating 
	 * autonomic managers and setting their capabilities and policies.
	 */
	public AutonomicManager createMgmtInfrastructure(Simulation simulation, DataCentre dc) {
		
		// Create DC Manager.
		HostPoolManager hostPool = new HostPoolManager();
		AutonomicManager dcManager = new AutonomicManager(simulation, hostPool);
		
		// Install management policies in the autonomic manager.
		dcManager.installPolicy(new ReactiveHostStatusPolicy(5));
		dcManager.installPolicy(new VmPlacementPolicyFFMHybrid(lower, upper, target));
		dcManager.installPolicy(new VmRelocationPolicyHybridReactive(lower, upper, target));
		dcManager.installPolicy(new VmConsolidationPolicyFFDDIHybrid(lower, upper, target), SimTime.hours(1), SimTime.hours(1));
		
		// TODO: Autonomic manager is NOT installed anywhere.
		
		for (Host host : dc.getHosts()) {
			
			// Create Host's autonomic manager.
			AutonomicManager hostManager = new AutonomicManager(simulation, new HostManager(host));
			
			// Install management policies in the autonomic manager.
			hostManager.installPolicy(new HostMonitoringPolicy(dcManager), SimTime.minutes(2), SimTime.minutes(simulation.getRandom().nextInt(5)));
			hostManager.installPolicy(new HostOperationsPolicy());
			
			// Install autonomic manager in the Host.
			host.installAutonomicManager(hostManager);
			
			// Add Host and its autonomic manager to the capability of the hosting Rack.
			hostPool.addHost(host, hostManager);
			
		}
		
		return dcManager;
	}
	
	public static void main(String args[]) {
		
		Simulation.initializeLogging();
		
		Collection<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
		
		executor.addTask(new ReactiveHybridStrategyExperiment("manfi2014-centralized-1", 6198910678692541341l));
//		executor.addTask(new ReactiveHybridStrategyExperiment("manfi2014-centralized-2", 5646441053220106016l));
//		executor.addTask(new ReactiveHybridStrategyExperiment("manfi2014-centralized-3", -5705302823151233610l));
//		executor.addTask(new ReactiveHybridStrategyExperiment("manfi2014-centralized-4", 8289672009575825404l));
//		executor.addTask(new ReactiveHybridStrategyExperiment("manfi2014-centralized-5", -4637549055860880177l));
		
		completedTasks = executor.execute();
		
		for(SimulationTask task : completedTasks) {
			logger.info(task.getName());
			task.getMetrics().printDefault(logger);
		}
	}

}
