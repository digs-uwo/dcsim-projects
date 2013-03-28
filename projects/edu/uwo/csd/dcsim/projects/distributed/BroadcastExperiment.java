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
import edu.uwo.csd.dcsim.projects.distributed.policies.VmPlacementPolicyBroadcast;

public class BroadcastExperiment extends SimulationTask {

	private static Logger logger = Logger.getLogger(BroadcastExperiment.class);
	
	private static final long DURATION = SimTime.days(5);
	private static final long METRIC_RECORD_START = SimTime.hours(0);
	
	public static void main(String args[]) {
		Simulation.initializeLogging();
		
		Collection<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
		
		executor.addTask(new BroadcastExperiment("broadcast-1", 6198910678692541341l));
//		executor.addTask(new BroadcastExperiment("broadcast-2", 5646441053220106016l));
//		executor.addTask(new BroadcastExperiment("broadcast-3", -5705302823151233610l));
//		executor.addTask(new BroadcastExperiment("broadcast-4", 8289672009575825404l));
//		executor.addTask(new BroadcastExperiment("broadcast-5", -4637549055860880177l));
		
		completedTasks = executor.execute();
		
		for(SimulationTask task : completedTasks) {
			logger.info(task.getName());
			DistributedTestEnvironment.printMetrics(task.getResults());
		}
		
	}
	
	public BroadcastExperiment(String name, long randomSeed) {
		super(name, DURATION);
		this.setMetricRecordStart(METRIC_RECORD_START);
		this.setRandomSeed(randomSeed);
	}

	@Override
	public void setup(Simulation simulation) {
		
		// Set utilization thresholds.
//		double lower = 0.60;
//		double upper = 0.90;
//		double target = 0.85;
		
		// Create data centre and its manager.
		Tuple<DataCentre, AutonomicManager> tuple = DistributedTestEnvironment.createDataCentre(simulation);
		AutonomicManager dcAM = tuple.b;
		
		// Create and install management policies for the data centre.
		dcAM.installPolicy(new VmPlacementPolicyBroadcast());
//		dcAM.installPolicy(new VmRelocationPolicyFFIMDHybrid(lower, upper, target), SimTime.minutes(10), SimTime.minutes(10) + 1);
//		dcAM.installPolicy(new VmConsolidationPolicyFFDDIHybrid(lower, upper, target), SimTime.hours(1), SimTime.hours(1) + 2);
		
		// Create and start ServiceProducer.
//		DistributedTestEnvironment.configureStaticServices(simulation, dcAM);
//		DistributedTestEnvironment.configureDynamicServices(simulation, dcAM);
		DistributedTestEnvironment.configureRandomServices(simulation, dcAM, 1, 30, 80);
		
	}

	
	
}
