package edu.uwo.csd.dcsim.projects.distributed;

import java.util.Collection;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.SimulationExecutor;
import edu.uwo.csd.dcsim.SimulationTask;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.common.Tuple;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.projects.centralized.PeriodicHybridStrategyExperiment;
import edu.uwo.csd.dcsim.projects.centralized.ReactiveHybridStrategyExperiment;
import edu.uwo.csd.dcsim.projects.distributed.policies.VmPlacementPolicyBroadcast;

public class BroadcastExperiment extends SimulationTask {

	private static Logger logger = Logger.getLogger(BroadcastExperiment.class);
	
	private static final long DURATION = SimTime.days(10);
	private static final long METRIC_RECORD_START = SimTime.days(2);
	
	public static void main(String args[]) {
		Simulation.initializeLogging();
		
		//broadcast
		Collection<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
//		
//		executor.addTask(new BroadcastExperiment("broadcast-1", 6198910678692541341l));
//		executor.addTask(new BroadcastExperiment("broadcast-2", 5646441053220106016l));
//		executor.addTask(new BroadcastExperiment("broadcast-3", -5705302823151233610l));
//		executor.addTask(new BroadcastExperiment("broadcast-4", 8289672009575825404l));
//		executor.addTask(new BroadcastExperiment("broadcast-5", -4637549055860880177l));
//		
//		completedTasks = executor.execute();
//		
//		for(SimulationTask task : completedTasks) {
//			logger.info(task.getName());
//			DistributedTestEnvironment.printMetrics(task.getResults());
//		}
		
		executor = new SimulationExecutor();
		
		executor.addTask(new BroadcastExperiment("broadcast-6", -4280782692131378509l));
		executor.addTask(new BroadcastExperiment("broadcast-7", -1699811527182374894l));
		executor.addTask(new BroadcastExperiment("broadcast-8", -6452776964812569334l));
		executor.addTask(new BroadcastExperiment("broadcast-9", -7148920787255940546l));
		executor.addTask(new BroadcastExperiment("broadcast-10", 8311271444423629559l));

		completedTasks = executor.execute();
		
		for(SimulationTask task : completedTasks) {
			logger.info(task.getName());
			DistributedTestEnvironment.printMetrics(task.getResults());
		}

//		
//		//periodic
//		executor = new SimulationExecutor();
//		
//		executor.addTask(new PeriodicHybridStrategyExperiment("periodic-1", 6198910678692541341l));
//		executor.addTask(new PeriodicHybridStrategyExperiment("periodic-2", 5646441053220106016l));
//		executor.addTask(new PeriodicHybridStrategyExperiment("periodic-3", -5705302823151233610l));
//		executor.addTask(new PeriodicHybridStrategyExperiment("periodic-4", 8289672009575825404l));
//		executor.addTask(new PeriodicHybridStrategyExperiment("periodic-5", -4637549055860880177l));
//		
//		completedTasks = executor.execute();
//		
//		for(SimulationTask task : completedTasks) {
//			logger.info(task.getName());
//			DistributedTestEnvironment.printMetrics(task.getResults());
//		}
//		
//		executor.addTask(new PeriodicHybridStrategyExperiment("periodic-6", -4280782692131378509l));
//		executor.addTask(new PeriodicHybridStrategyExperiment("periodic-7", -1699811527182374894l));
//		executor.addTask(new PeriodicHybridStrategyExperiment("periodic-8", -6452776964812569334l));
//		executor.addTask(new PeriodicHybridStrategyExperiment("periodic-9", -7148920787255940546l));
//		executor.addTask(new PeriodicHybridStrategyExperiment("periodic-10", 8311271444423629559l));
//		
//		completedTasks = executor.execute();
//		
//		for(SimulationTask task : completedTasks) {
//			logger.info(task.getName());
//			DistributedTestEnvironment.printMetrics(task.getResults());
//		}
//		
//		//reactive
//		executor = new SimulationExecutor();
//		
//		executor.addTask(new ReactiveHybridStrategyExperiment("reactive-1", 6198910678692541341l));
//		executor.addTask(new ReactiveHybridStrategyExperiment("reactive-2", 5646441053220106016l));
//		executor.addTask(new ReactiveHybridStrategyExperiment("reactive-3", -5705302823151233610l));
//		executor.addTask(new ReactiveHybridStrategyExperiment("reactive-4", 8289672009575825404l));
//		executor.addTask(new ReactiveHybridStrategyExperiment("reactive-5", -4637549055860880177l));
//		
//		completedTasks = executor.execute();
//		
//		for(SimulationTask task : completedTasks) {
//			logger.info(task.getName());
//			DistributedTestEnvironment.printMetrics(task.getResults());
//		}
//		
//		executor.addTask(new ReactiveHybridStrategyExperiment("reactive-6", -4280782692131378509l));
//		executor.addTask(new ReactiveHybridStrategyExperiment("reactive-7", -1699811527182374894l));
//		executor.addTask(new ReactiveHybridStrategyExperiment("reactive-8", -6452776964812569334l));
//		executor.addTask(new ReactiveHybridStrategyExperiment("reactive-9", -7148920787255940546l));
//		executor.addTask(new ReactiveHybridStrategyExperiment("reactive-10", 8311271444423629559l));
//		
//		completedTasks = executor.execute();
//		
//		for(SimulationTask task : completedTasks) {
//			logger.info(task.getName());
//			DistributedTestEnvironment.printMetrics(task.getResults());
//		}
		
	}
	
	public BroadcastExperiment(String name, long randomSeed) {
		super(name, DURATION);
		this.setMetricRecordStart(METRIC_RECORD_START);
		this.setRandomSeed(randomSeed);
	}

	@Override
	public void setup(Simulation simulation) {
		
		// Set utilization thresholds.
		double lower = 0.6;
		double upper = 0.9;
		double target = 0.85;
		
		// Create data centre and its manager.
		Tuple<DataCentre, AutonomicManager> tuple = DistributedTestEnvironment.createDataCentre(simulation, lower, upper, target);
		AutonomicManager dcAM = tuple.b;
		
		// Create and install management policies for the data centre.
		dcAM.installPolicy(new VmPlacementPolicyBroadcast(lower, upper, target));
//		dcAM.installPolicy(new VmRelocationPolicyFFIMDHybrid(lower, upper, target), SimTime.minutes(10), SimTime.minutes(10) + 1);
//		dcAM.installPolicy(new VmConsolidationPolicyFFDDIHybrid(lower, upper, target), SimTime.hours(1), SimTime.hours(1) + 2);
		
		// Create and start ServiceProducer.
//		DistributedTestEnvironment.configureStaticServices(simulation, dcAM);
//		DistributedTestEnvironment.configureDynamicServices(simulation, dcAM);
//		DistributedTestEnvironment.configureRandomServices(simulation, dcAM, 1, 100, 350);
//		DistributedTestEnvironment.configureRandomServices(simulation, dcAM, 1, 40, 100);
		DistributedTestEnvironment.configureRandomServices(simulation, dcAM, 1, 600, 1600); //for 200 hosts
//		DistributedTestEnvironment.configureRandomServices(simulation, dcAM, 1, 1400, 4000); //for 500 hosts
//		DistributedTestEnvironment.configureRandomServices(simulation, dcAM, 1, 3000, 8000); //for 1000 hosts
		
	}

	
	
}

