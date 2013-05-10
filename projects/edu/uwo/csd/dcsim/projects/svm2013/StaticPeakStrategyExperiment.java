package edu.uwo.csd.dcsim.projects.svm2013;

import java.util.Collection;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.common.*;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.policies.HostStatusPolicy;
import edu.uwo.csd.dcsim.projects.svm2013.policies.VmPlacementPolicyStaticPeak;

/**
 * This class serves to evaluate the Static Hybrid Strategy in the test 
 * environment for the SVM 2013 paper. The strategy consists only of the 
 * following policy:
 * 
 * + VmPlacementPolicyStaticPeak
 * 
 * The VM Placement policy runs as needed, mapping VMs into Hosts without 
 * over-subscribing the resources of the latter.
 * 
 * @author Gaston Keller
 *
 */
public class StaticPeakStrategyExperiment extends SimulationTask {

	private static Logger logger = Logger.getLogger(StaticPeakStrategyExperiment.class);
	
	public StaticPeakStrategyExperiment(String name, long randomSeed) {
		super(name, SimTime.days(10));					// 10-day simulation
		this.setMetricRecordStart(SimTime.days(2));		// start on 3rd day (i.e., after 2 days)
		this.setRandomSeed(randomSeed);
	}

	@Override
	public void setup(Simulation simulation) {
		// Create data centre and its manager.
		Tuple<DataCentre, AutonomicManager> tuple = SVM2013TestEnvironment.createDataCentre(simulation);
		AutonomicManager dcAM = tuple.b;
		
		// Create and install policy to manage Host status updates.
		dcAM.installPolicy(new HostStatusPolicy(5));
		
		// Create and install management policies for the data centre.
		dcAM.installPolicy(new VmPlacementPolicyStaticPeak());
		
		// Create and start ServiceProducer.
		SVM2013TestEnvironment.configureRandomServices(simulation, dcAM, 1, 600, 1600);
	}
	
	public static void main(String args[]) {
		
		Simulation.initializeLogging();
		
		Collection<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
		
		executor.addTask(new StaticPeakStrategyExperiment("svm2013-static-peak-1", 6198910678692541341l));
//		executor.addTask(new HybridStrategyExperiment("svm2013-static-peak-2", 5646441053220106016l));
//		executor.addTask(new HybridStrategyExperiment("svm2013-static-peak-3", -5705302823151233610l));
//		executor.addTask(new HybridStrategyExperiment("svm2013-static-peak-4", 8289672009575825404l));
//		executor.addTask(new HybridStrategyExperiment("svm2013-static-peak-5", -4637549055860880177l));
		
		completedTasks = executor.execute();
		
		for(SimulationTask task : completedTasks) {
			logger.info(task.getName());
			SVM2013TestEnvironment.printMetrics(task.getResults());
			
			SimulationTraceWriter traceWriter = new SimulationTraceWriter(task);
			traceWriter.writeTrace();
		}
	}

}
