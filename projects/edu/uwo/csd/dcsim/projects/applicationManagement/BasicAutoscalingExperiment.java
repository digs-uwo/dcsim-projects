package edu.uwo.csd.dcsim.projects.applicationManagement;

import java.util.List;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.SimulationExecutor;
import edu.uwo.csd.dcsim.SimulationTask;
import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Cluster;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Rack;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.capabilities.*;
import edu.uwo.csd.dcsim.management.events.ApplicationPlacementEvent;
import edu.uwo.csd.dcsim.management.policies.*;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.ApplicationManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.policies.*;

public class BasicAutoscalingExperiment extends SimulationTask {

	private static Logger logger = Logger.getLogger(BasicAutoscalingExperiment.class);
	
	private static final long DURATION = SimTime.days(1);
//	private static final long DURATION = SimTime.minutes(5);
	private static final long METRIC_RECORD_START = SimTime.days(0);
	
	public static void main(String args[]) {
		Simulation.initializeLogging();
		
		//broadcast
		List<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
		
		executor.addTask(new BasicAutoscalingExperiment("autoscaling-1", 6198910678692541341l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-2", 5646441053220106016l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-3", -5705302823151233610l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-4", 8289672009575825404l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-5", -4637549055860880177l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-6", -4280782692131378509l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-7", -1699811527182374894l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-8", -6452776964812569334l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-9", -7148920787255940546l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-10", 8311271444423629559l));		
		
		
		completedTasks = executor.execute(); //execute all simulations simultaneously
//		completedTasks = executor.execute(4); //execute 4 simulations (i.e. 4 threads) at a time
		
		for(SimulationTask task : completedTasks) {
			logger.info(task.getName());
			task.getMetrics().printDefault(logger);
		}
		
		//output CSV
//		for(SimulationTask task : completedTasks) {
//			if (completedTasks.indexOf(task) == 0) {
//				task.getMetrics().printCSV(System.out);
//			} else {
//				task.getMetrics().printCSV(System.out, false);
//			}
//		}

	}
	
	public BasicAutoscalingExperiment(String name) {
		super(name, DURATION);
		this.setMetricRecordStart(METRIC_RECORD_START);
	}
	
	public BasicAutoscalingExperiment(String name, long randomSeed) {
		super(name, DURATION);
		this.setMetricRecordStart(METRIC_RECORD_START);
		this.setRandomSeed(randomSeed);
	}

	@Override
	public void setup(Simulation simulation) {
		
		simulation.getSimulationMetrics().addCustomMetricCollection(new ApplicationManagementMetrics(simulation));
		
		Environment environment = new Environment(simulation, 10, 2);
		environment.createDataCentre(simulation);
		
		simulation.sendEvent(new ApplicationPlacementEvent(environment.getDcAM(), environment.createApplication()));
		
		simulation.sendEvent(new ApplicationPlacementEvent(environment.getDcAM(), environment.createApplication()), SimTime.minutes(1));
		
		simulation.sendEvent(new ApplicationPlacementEvent(environment.getDcAM(), environment.createApplication()), SimTime.minutes(2));
		
		simulation.sendEvent(new ApplicationPlacementEvent(environment.getDcAM(), environment.createApplication()), SimTime.minutes(3));
	}
	
	public class Environment extends AppManagementTestEnvironment {

		HostPoolManager hostPool;
		
		public Environment(Simulation simulation, int hostsPerRack, int nRacks) {
			super(simulation, hostsPerRack, nRacks);
		}

		@Override
		public void processDcAM(AutonomicManager dcAM) {
			hostPool = new HostPoolManager();
			dcAM.addCapability(hostPool);
			
			dcAM.installPolicy(new HostStatusPolicy(10));
			dcAM.installPolicy(new ApplicationPlacementPolicy());
			
		}

		@Override
		public void processHost(Host host, Rack rack, Cluster cluster, DataCentre dc, AutonomicManager dcAM) {
			AutonomicManager hostAM = new AutonomicManager(simulation, new HostManager(host));
			hostAM.installPolicy(new HostMonitoringPolicy(dcAM), SimTime.minutes(5), SimTime.minutes(envRandom.nextInt(5)));
			hostAM.installPolicy(new HostOperationsPolicy());

			host.installAutonomicManager(hostAM);
			
			hostPool.addHost(host, hostAM);
		}

		@Override
		public void processApplication(InteractiveApplication application) {
			AutonomicManager applicationManager = new AutonomicManager(simulation);
			applicationManager.addCapability(new ApplicationManager(application));
			
			applicationManager.installPolicy(new ApplicationScalingPolicy(dcAM), SimTime.minutes(5), 0);
		}
		
	}

}
