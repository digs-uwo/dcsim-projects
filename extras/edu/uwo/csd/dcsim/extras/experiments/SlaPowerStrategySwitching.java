package edu.uwo.csd.dcsim.extras.experiments;

import java.util.Collection;

import org.apache.log4j.*;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.core.*;
import edu.uwo.csd.dcsim.extras.policies.*;
import edu.uwo.csd.dcsim.management.VMPlacementPolicy;

/**
 * This simulation switching strategies between a power-friendly and SLA-friendly strategy.
 * 
 * @author Michael Tighe
 *
 */
public class SlaPowerStrategySwitching extends DCSimulationTask {

	private static Logger logger = Logger.getLogger(SlaPowerStrategySwitching.class);
	
	private double slaHigh, slaNormal, powerHigh, powerNormal;
	
	public static void main(String args[]) {
		
		Simulation.initializeLogging();
		
		Collection<DCSimulationTask> completedTasks;
		SimulationExecutor<DCSimulationTask> executor = new SimulationExecutor<DCSimulationTask>();
		
		executor.addTask(new SlaPowerStrategySwitching("slapow-strat-switching-1", 6198910678692541341l));
//		executor.addTask(new SlaPowerStrategySwitching("slapow-strat-switching-2", 5646441053220106016l));
//		executor.addTask(new SlaPowerStrategySwitching("slapow-strat-switching-3", -5705302823151233610l));
//		executor.addTask(new SlaPowerStrategySwitching("slapow-strat-switching-4", 8289672009575825404l));
//		executor.addTask(new SlaPowerStrategySwitching("slapow-strat-switching-5", -4637549055860880177l));
		
		completedTasks = executor.execute();
		
//		DCSimulationReport report = new DCSimulationReport("strat-switching", completedTasks);
//		report.logResults();
		
		for(DCSimulationTask task : completedTasks) {
			logger.info(task.getName());
			IM2012TestEnvironment.printMetrics(task.getResults());
			
			DCSimulationTraceWriter traceWriter = new DCSimulationTraceWriter(task);
			traceWriter.writeTrace();
		}

	}

	/**
	 * Constructs a Strategy Switching task with default thresholds
	 * @param name
	 * @param randomSeed
	 */
	public SlaPowerStrategySwitching(String name, long randomSeed) {
		super(name, SimTime.days(10));					// 10-day simulation
		this.setMetricRecordStart(SimTime.days(2));	// start on 3rd day (i.e. after 2 days)
		this.setRandomSeed(randomSeed);
		this.slaHigh = 0.008;
		this.slaNormal = 0.002;
		this.powerHigh = 1.35;
		this.powerNormal = 1.2;
	}
	
	/**
	 * Constructs a Strategy Switching task with supplied thresholds
	 * @param name
	 * @param randomSeed
	 * @param slaHigh
	 * @param slaNormal
	 * @param powerHigh
	 * @param powerNormal
	 */
	public SlaPowerStrategySwitching(String name, long randomSeed, double slaHigh, double slaNormal, double powerHigh, double powerNormal){
		super(name, SimTime.days(10));					// 10-day simulation
		this.setMetricRecordStart(SimTime.days(2));	// start on 3rd day (i.e. after 2 days)
		this.setRandomSeed(randomSeed);
		this.slaHigh = slaHigh;
		this.slaNormal = slaNormal;
		this.powerHigh = powerHigh;
		this.powerNormal = powerNormal;
	}

	@Override
	public void setup(DataCentreSimulation simulation) {

		// Create data centre (with default VM Placement policy).
		DataCentre dc = IM2012TestEnvironment.createDataCentre(simulation);
		simulation.addDatacentre(dc);
		
		// Create CPU load utilization monitor.
		DCUtilizationMonitor dcMon = new DCUtilizationMonitor(simulation, 120000, 5, dc);
		simulation.addMonitor(dcMon);
		

		// Create and start ServiceProducer.
//		IM2012TestEnvironment.configureStaticServices(simulation, dc);
//		IM2012TestEnvironment.configureDynamicServices(simulation, dc);
		IM2012TestEnvironment.configureRandomServices(simulation, dc, 1, 600, 1600);
		
		
		/*
		 * Create Power-friendly Strategy
		 */
		
		// Set utilization thresholds.
		double powerLower = 0.6;
		double powerUpper = 0.95;	// 0.90
		double powerTarget = 0.90;	// 0.85
		
		// Create and set desired VM Placement policy for the data centre.
		VMPlacementPolicy powerVMPlacementPolicy = new VMPlacementPolicyFFDGreen(simulation, dc, dcMon, powerLower, powerUpper, powerTarget);
		
		// Relocation policy
		VMRelocationPolicyFFIDGreen powerRelocationPolicy = new VMRelocationPolicyFFIDGreen(dc, dcMon, powerLower, powerUpper, powerTarget);
		DaemonScheduler powerRelocationPolicyDaemon = new FixedIntervalDaemonScheduler(simulation, 600000, powerRelocationPolicy);
		
		// Consolidation policy
		VMConsolidationPolicyFFDDIGreen powerConsolidationPolicy = new VMConsolidationPolicyFFDDIGreen(dc, dcMon, powerLower, powerUpper, powerTarget);
		DaemonScheduler powerConsolidationPolicyDaemon = new FixedIntervalDaemonScheduler(simulation, 3600000, powerConsolidationPolicy);

		DaemonSchedulerGroup powerDaemonGroup = new DaemonSchedulerGroup(simulation);
		powerDaemonGroup.addDaemon(powerRelocationPolicyDaemon, 600000);
		powerDaemonGroup.addDaemon(powerConsolidationPolicyDaemon, 3601000);
		
		
		
		/*
		 * Create SLA-friendly Strategy
		 */
		
		// Set utilization thresholds.
		double slaLower = 0.6;
		double slaUpper = 0.85;
		double slaTarget = 0.8;
		
		// Create and set desired VM Placement policy for the data centre.
		VMPlacementPolicy slaVMPlacementPolicy = new VMPlacementPolicyFFMSla(simulation, dc, dcMon, slaLower, slaUpper, slaTarget);
		
		// Relocation policy
		VMRelocationPolicyFFIMSla slaRelocationPolicy = new VMRelocationPolicyFFIMSla(dc, dcMon, slaLower, slaUpper, slaTarget);
		DaemonScheduler slaRelocationPolicyDaemon = new FixedIntervalDaemonScheduler(simulation, 600000, slaRelocationPolicy);
		
		// Consolidation policy
		VMConsolidationPolicyFFDMISla slaConsolidationPolicy = new VMConsolidationPolicyFFDMISla(dc, dcMon, slaLower, slaUpper, slaTarget);
		DaemonScheduler slaConsolidationPolicyDaemon = new FixedIntervalDaemonScheduler(simulation, 14400000, slaConsolidationPolicy);

		DaemonSchedulerGroup slaDaemonGroup = new DaemonSchedulerGroup(simulation);
		slaDaemonGroup.addDaemon(slaRelocationPolicyDaemon, 600000);
		slaDaemonGroup.addDaemon(slaConsolidationPolicyDaemon, 14401000);
		
		/*
		 * Configure strategy switching
		 */
		
		//currently configured so that only SLA value is used 
		SlaVsPowerStrategySwitchPolicy switchingPolicy = new SlaVsPowerStrategySwitchPolicy.Builder(dc, dcMon)
			.slaPolicy(slaDaemonGroup, slaVMPlacementPolicy)
			.powerPolicy(powerDaemonGroup, powerVMPlacementPolicy)
			.startingPolicy(powerDaemonGroup)
			.slaHigh(slaHigh)
			.slaNormal(slaNormal)
			.powerHigh(powerHigh)
			.powerNormal(powerNormal)
			.build();
		
		DaemonScheduler policyDaemon = new FixedIntervalDaemonScheduler(simulation, SimTime.hours(4), switchingPolicy);
		policyDaemon.start(SimTime.hours(1) - SimTime.seconds(1)); 
	}

}
