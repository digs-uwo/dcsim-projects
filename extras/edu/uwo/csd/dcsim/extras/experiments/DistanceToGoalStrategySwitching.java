package edu.uwo.csd.dcsim.extras.experiments;

import java.util.Collection;

import org.apache.log4j.*;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.core.*;
import edu.uwo.csd.dcsim.extras.policies.*;
import edu.uwo.csd.dcsim.management.VMPlacementPolicy;

/**
 * This simulation switches between Power and SLA-friendly management 
 * strategies based on distance to goals.
 * 
 * @author Gaston Keller
 *
 */
public class DistanceToGoalStrategySwitching extends DCSimulationTask {

	private static Logger logger = Logger.getLogger(DistanceToGoalStrategySwitching.class);
	
	private double worstSlaGoal;		// Worst SLA violations Goal. Expected (or desired) worst value.
	private double worstPowerEffCo;		// Worst Power Efficiency Coefficient, as percentage (0,1) of the current Optimal Power Efficiency metric.
	
	public static void main(String args[]) {
		
		Simulation.initializeLogging();
		
		Collection<DCSimulationTask> completedTasks;
		SimulationExecutor<DCSimulationTask> executor = new SimulationExecutor<DCSimulationTask>();
		
		executor.addTask(new DistanceToGoalStrategySwitching("goal-strat-switching-1", 6198910678692541341l));
//		executor.addTask(new DistanceToGoalStrategySwitching("goal-strat-switching-2", 5646441053220106016l));
//		executor.addTask(new DistanceToGoalStrategySwitching("goal-strat-switching-3", -5705302823151233610l));
//		executor.addTask(new DistanceToGoalStrategySwitching("goal-strat-switching-4", 8289672009575825404l));
//		executor.addTask(new DistanceToGoalStrategySwitching("goal-strat-switching-5", -4637549055860880177l));
		
		completedTasks = executor.execute();
		
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
	public DistanceToGoalStrategySwitching(String name, long randomSeed) {
		super(name, SimTime.days(10));					// 10-day simulation
		this.setMetricRecordStart(SimTime.days(2));		// start on 3rd day (i.e. after 2 days)
		this.setRandomSeed(randomSeed);
//		this.worstSlaGoal = 0.005;						// 0.5% SLA violations as the worst SLA violations value desired
		this.worstSlaGoal = 0.005;
//		this.worstPowerEffCo = 0.80;					// 75% Optimal Power Efficiency as the worst power efficiency desired
		this.worstPowerEffCo = 0.85;
	}
	
	/**
	 * Constructs a Strategy Switching task with supplied thresholds
	 * @param name
	 * @param randomSeed
	 * @param worstSlaGoal
	 * @param worstPowerEffCo
	 */
	public DistanceToGoalStrategySwitching(String name, long randomSeed, double worstSlaGoal, double worstPowerEffCo){
		super(name, SimTime.days(10));					// 10-day simulation
		this.setMetricRecordStart(SimTime.days(2));		// start on 3rd day (i.e. after 2 days)
		this.setRandomSeed(randomSeed);
		this.worstSlaGoal = worstSlaGoal;
		this.worstPowerEffCo = worstPowerEffCo;
	}

	@Override
	public void setup(DataCentreSimulation simulation) {

		// Create data centre (with default VM Placement policy).
		DataCentre dc = IM2012TestEnvironment.createDataCentre(simulation);
		simulation.addDatacentre(dc);
		
		// Create CPU load utilization monitor (for use with policy-sets).
		DCUtilizationMonitor hostsMon = new DCUtilizationMonitor(simulation, SimTime.minutes(2), 5, dc);
		simulation.addMonitor(hostsMon);
		
		// Create CPU load utilization monitor (for use with strategy switching).
		DCUtilizationMonitor dcMon = new DCUtilizationMonitor(simulation, SimTime.minutes(2), 5, dc);
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
		VMPlacementPolicy powerVMPlacementPolicy = new VMPlacementPolicyFFDGreen(simulation, dc, hostsMon, powerLower, powerUpper, powerTarget);
		
		// Relocation policy
		VMRelocationPolicyFFIDGreen powerRelocationPolicy = new VMRelocationPolicyFFIDGreen(dc, hostsMon, powerLower, powerUpper, powerTarget);
		DaemonScheduler powerRelocationPolicyDaemon = new FixedIntervalDaemonScheduler(simulation, 600000, powerRelocationPolicy);
		
		// Consolidation policy
		VMConsolidationPolicyFFDDIGreen powerConsolidationPolicy = new VMConsolidationPolicyFFDDIGreen(dc, hostsMon, powerLower, powerUpper, powerTarget);
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
		VMPlacementPolicy slaVMPlacementPolicy = new VMPlacementPolicyFFMSla(simulation, dc, hostsMon, slaLower, slaUpper, slaTarget);
		
		// Relocation policy
		VMRelocationPolicyFFIMSla slaRelocationPolicy = new VMRelocationPolicyFFIMSla(dc, hostsMon, slaLower, slaUpper, slaTarget);
		DaemonScheduler slaRelocationPolicyDaemon = new FixedIntervalDaemonScheduler(simulation, 600000, slaRelocationPolicy);
		
		// Consolidation policy
		VMConsolidationPolicyFFDMISla slaConsolidationPolicy = new VMConsolidationPolicyFFDMISla(dc, hostsMon, slaLower, slaUpper, slaTarget);
		DaemonScheduler slaConsolidationPolicyDaemon = new FixedIntervalDaemonScheduler(simulation, 14400000, slaConsolidationPolicy);

		DaemonSchedulerGroup slaDaemonGroup = new DaemonSchedulerGroup(simulation);
		slaDaemonGroup.addDaemon(slaRelocationPolicyDaemon, 600000);
		slaDaemonGroup.addDaemon(slaConsolidationPolicyDaemon, 14401000);
		
		/*
		 * Configure strategy switching
		 */
		DistanceToGoalStrategySwitchPolicy switchingPolicy = new DistanceToGoalStrategySwitchPolicy.Builder(dc, dcMon)
			.slaPolicy(slaDaemonGroup, slaVMPlacementPolicy)
			.powerPolicy(powerDaemonGroup, powerVMPlacementPolicy)
			.startingPolicy(powerDaemonGroup)
			.worstSlaGoal(worstSlaGoal)
			.worstPowerEffCo(worstPowerEffCo)
			.build();
		
		DaemonScheduler policyDaemon = new FixedIntervalDaemonScheduler(simulation, SimTime.hours(1), switchingPolicy);
		policyDaemon.start(SimTime.hours(1) - SimTime.seconds(1)); 
	}

}
