package edu.uwo.csd.dcsim.extras.experiments;

import java.util.Collection;

import org.apache.log4j.*;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.core.*;
import edu.uwo.csd.dcsim.extras.policies.*;

/**
 * This class serves to test the set of policies that conform the VM 
 * Allocation Strategy found in "Optimal Online Deterministic Algorithms and 
 * Adaptive Heuristics for Energy and Performance Efficient Dynamic 
 * Consolidation of Virtual Machines in Cloud Data Centers", Anton Beloglazov 
 * and Rajkumar Buyya, Concurrency Computat.: Pract. Exper. 2011; 00:1-24.
 * 
 * Policies:
 * + VMPlacementPolicyPABFD
 * + VMAllocationPolicyPABFD
 *   
 * @author Gaston Keller
 *
 */
public class AntonsStrategy extends DCSimulationTask {

	private static Logger logger = Logger.getLogger(AntonsStrategy.class);
	
	public static void main(String args[]) {
		
		Simulation.initializeLogging();
		
		Collection<DCSimulationTask> completedTasks;
		SimulationExecutor<DCSimulationTask> executor = new SimulationExecutor<DCSimulationTask>();
		
		executor.addTask(new AntonsStrategy("anton-1", 6198910678692541341l));
//		executor.addTask(new AntonsStrategy("anton-2", 5646441053220106016l));
//		executor.addTask(new AntonsStrategy("anton-3", -5705302823151233610l));
//		executor.addTask(new AntonsStrategy("anton-4", 8289672009575825404l));
//		executor.addTask(new AntonsStrategy("anton-5", -4637549055860880177l));
		
		completedTasks = executor.execute();
		
		for(DCSimulationTask task : completedTasks) {
			logger.info(task.getName());
			IM2012TestEnvironment.printMetrics(task.getResults());
			
			DCSimulationTraceWriter traceWriter = new DCSimulationTraceWriter(task);
			traceWriter.writeTrace();
		}

	}

	public AntonsStrategy(String name, long randomSeed) {
		super(name, SimTime.days(10));					// 10-day simulation
		this.setMetricRecordStart(SimTime.days(2));		// start on 3rd day (i.e. after 2 days)
		this.setRandomSeed(randomSeed);
	}

	@Override
	public void setup(DataCentreSimulation simulation) {
		// Set utilization thresholds.
		double lower = 0.6;
		double upper = 0.95;	// 0.90
		
		// Create data centre (with default VM Placement policy).
		DataCentre dc = IM2012TestEnvironment.createDataCentre(simulation);
		simulation.addDatacentre(dc);
		
		// Create CPU load utilization monitor.
		DCUtilizationMonitor dcMon = new DCUtilizationMonitor(simulation, SimTime.minutes(2), 5, dc);
		simulation.addMonitor(dcMon);
		
		// Create and set desired VM Placement policy for the data centre.
		dc.setVMPlacementPolicy(new VMPlacementPolicyPABFD(simulation, dc, dcMon, lower, upper));
		
		// Create and start ServiceProducer.
//		IM2012TestEnvironment.configureStaticServices(simulation, dc);
//		IM2012TestEnvironment.configureDynamicServices(simulation, dc);
		IM2012TestEnvironment.configureRandomServices(simulation, dc, 1, 600, 1600);
		
		/*
		 * VM Allocation (VM Relocation + VM Consolidation) policy.
		 */
		VMAllocationPolicyPABFD vmAllocationPolicy = new VMAllocationPolicyPABFD(dc, dcMon, lower, upper);
		
		DaemonScheduler allocationPolicyDaemon = new FixedIntervalDaemonScheduler(simulation, SimTime.minutes(10), vmAllocationPolicy);
		allocationPolicyDaemon.start(SimTime.minutes(10));
	}

}
