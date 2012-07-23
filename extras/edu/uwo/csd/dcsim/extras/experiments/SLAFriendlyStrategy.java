package edu.uwo.csd.dcsim.extras.experiments;

import java.util.Collection;

import org.apache.log4j.*;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.core.*;
import edu.uwo.csd.dcsim.extras.policies.*;

/**
 * This class serves to test the set of policies that conform the SLA-friendly 
 * Strategy:
 * 
 * + VMPlacementPolicyFFMSla
 * + VMRelocationPolicyFFIMSla
 * + VMConsolidationPolicyFFDMISla
 *   
 * @author Gaston Keller
 *
 */
public class SLAFriendlyStrategy extends DCSimulationTask {

	private static Logger logger = Logger.getLogger(SLAFriendlyStrategy.class);
	
	public static void main(String args[]) {
		
		Simulation.initializeLogging();
		
		Collection<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
		
		executor.addTask(new SLAFriendlyStrategy("sla-friendly-1", 6198910678692541341l));
//		executor.addTask(new SLAFriendlyStrategy("sla-friendly-2", 5646441053220106016l));
//		executor.addTask(new SLAFriendlyStrategy("sla-friendly-3", -5705302823151233610l));
//		executor.addTask(new SLAFriendlyStrategy("sla-friendly-4", 8289672009575825404l));
//		executor.addTask(new SLAFriendlyStrategy("sla-friendly-5", -4637549055860880177l));
		
		completedTasks = executor.execute();
		
		for(SimulationTask task : completedTasks) {
			logger.info(task.getName());
			IM2012TestEnvironment.printMetrics(task.getResults());
		}

	}

	public SLAFriendlyStrategy(String name, long randomSeed) {
		super(name, 864000000);
		this.setMetricRecordStart(86400000);
		this.setRandomSeed(randomSeed);
	}

	@Override
	public void setup(DataCentreSimulation simulation) {
		// Set utilization thresholds.
		double lower = 0.6;
		double upper = 0.85;
		double target = 0.85;
		
		// Create data centre (with default VM Placement policy).
		DataCentre dc = IM2012TestEnvironment.createDataCentre(simulation);
		simulation.addDatacentre(dc);
		
		// Create CPU load utilization monitor.
		DCUtilizationMonitor dcMon = new DCUtilizationMonitor(simulation, 120000, 5, dc);
		simulation.addMonitor(dcMon);
		
		// Create and set desired VM Placement policy for the data centre.
		dc.setVMPlacementPolicy(new VMPlacementPolicyFFMSla(simulation, dc, dcMon, lower, upper, target));
		
		// Create and start ServiceProducer.
		IM2012TestEnvironment.createServiceProducer(simulation, dc).start();
		
		/*
		 * Relocation policies.
		 */
		VMRelocationPolicyFFIMSla vmRelocationPolicy = new VMRelocationPolicyFFIMSla(dc, dcMon, lower, upper, target);
		
		DaemonScheduler relocationPolicyDaemon = new FixedIntervalDaemonScheduler(simulation, 600000, vmRelocationPolicy);
		relocationPolicyDaemon.start(600000);
		
		/*
		 * Consolidation policies.
		 */
		VMConsolidationPolicyFFDMISla vmConsolidationPolicy = new VMConsolidationPolicyFFDMISla(dc, dcMon, lower, upper, target);
		
		DaemonScheduler consolidationPolicyDaemon = new FixedIntervalDaemonScheduler(simulation, 14400000, vmConsolidationPolicy);
		//consolidationPolicyDaemon.start(3601000);		//  1 hour
		consolidationPolicyDaemon.start(14401000);		//  4 hours
		//consolidationPolicyDaemon.start(86401000);		// 24 hours

	}

}