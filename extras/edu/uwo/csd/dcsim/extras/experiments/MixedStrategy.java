package edu.uwo.csd.dcsim.extras.experiments;

import java.util.Collection;

import org.apache.log4j.*;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.core.*;
import edu.uwo.csd.dcsim.extras.policies.*;

/**
 * This class serves to test the set of policies that conform the Sla-Power 
 * Strategy, which does VM Placement and VM Relocation as the SLA-friendly 
 * Strategy and VM Consolidation as the Power Strategy:
 * 
 * + VMPlacementPolicyFFMSla
 * + VMRelocationPolicyFFIMSla
 * + VMConsolidationPolicyFFDDIGreen
 *   
 * @author Gaston Keller
 *
 */
public class MixedStrategy extends DCSimulationTask {

	private static Logger logger = Logger.getLogger(MixedStrategy.class);
	
	public static void main(String args[]) {
		
		Simulation.initializeLogging();
		
		Collection<DCSimulationTask> completedTasks;
		SimulationExecutor<DCSimulationTask> executor = new SimulationExecutor<DCSimulationTask>();
		
		executor.addTask(new MixedStrategy("mixed-1", 6198910678692541341l));
//		executor.addTask(new MixedStrategy("mixed-2", 5646441053220106016l));
//		executor.addTask(new MixedStrategy("mixed-3", -5705302823151233610l));
//		executor.addTask(new MixedStrategy("mixed-4", 8289672009575825404l));
//		executor.addTask(new MixedStrategy("mixed-5", -4637549055860880177l));
		
		completedTasks = executor.execute();
		
		for(DCSimulationTask task : completedTasks) {
			logger.info(task.getName());
			IM2012TestEnvironment.printMetrics(task.getResults());
			
			DCSimulationTraceWriter traceWriter = new DCSimulationTraceWriter(task);
			traceWriter.writeTrace();
		}

	}

	public MixedStrategy(String name, long randomSeed) {
		super(name, SimTime.days(10));					// 10-day simulation
		this.setMetricRecordStart(SimTime.days(2));	// start on 3rd day (i.e. after 2 days)
		this.setRandomSeed(randomSeed);
	}

	@Override
	public void setup(DataCentreSimulation simulation) {
		// Set utilization thresholds as SLA-friendly Strategy.
		double slaLower = 0.6;
		double slaUpper = 0.85;
		double slaTarget = 0.80;
		
		// Set utilization thresholds as Power Strategy.
		double powerLower = 0.6;
		double powerUpper = 0.95;
		double powerTarget = 0.90;
		
		// Create data centre (with default VM Placement policy).
		DataCentre dc = IM2012TestEnvironment.createDataCentre(simulation);
		simulation.addDatacentre(dc);
		
		// Create CPU load utilization monitor.
		DCUtilizationMonitor hostsMon = new DCUtilizationMonitor(simulation, SimTime.minutes(2), 5, dc);
		simulation.addMonitor(hostsMon);
		
		// Create and set desired VM Placement policy for the data centre.
		dc.setVMPlacementPolicy(new VMPlacementPolicyFFMSla(simulation, dc, hostsMon, slaLower, slaUpper, slaTarget));
		
		// Create and start ServiceProducer.
//		IM2012TestEnvironment.configureStaticServices(simulation, dc);
//		IM2012TestEnvironment.configureDynamicServices(simulation, dc);
		IM2012TestEnvironment.configureRandomServices(simulation, dc, 1, 600, 1600);
		
		/*
		 * Relocation policies.
		 */
		VMRelocationPolicyFFIMSla vmRelocationPolicy = new VMRelocationPolicyFFIMSla(dc, hostsMon, slaLower, slaUpper, slaTarget);
		
		DaemonScheduler relocationPolicyDaemon = new FixedIntervalDaemonScheduler(simulation, 600000, vmRelocationPolicy);
		relocationPolicyDaemon.start(600000);
		
		/*
		 * Consolidation policies.
		 */
		VMConsolidationPolicyFFDDIGreen vmConsolidationPolicy = new VMConsolidationPolicyFFDDIGreen(dc, hostsMon, powerLower, powerUpper, powerTarget);
		
		DaemonScheduler consolidationPolicyDaemon = new FixedIntervalDaemonScheduler(simulation, 3600000, vmConsolidationPolicy);
		consolidationPolicyDaemon.start(3601000);		//  1 hour
		//consolidationPolicyDaemon.start(14401000);		//  4 hours
		//consolidationPolicyDaemon.start(86401000);		// 24 hours
	}
}
