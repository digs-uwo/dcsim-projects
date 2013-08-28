package edu.uwo.csd.dcsim.projects.applicationManagement;

import java.util.List;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.SimulationExecutor;
import edu.uwo.csd.dcsim.SimulationTask;
import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.application.ApplicationListener;
import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.application.TaskInstance;
import edu.uwo.csd.dcsim.application.loadbalancer.EqualShareLoadBalancer;
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
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.TaskInstanceManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.policies.*;
import edu.uwo.csd.dcsim.projects.centralized.policies.VmConsolidationPolicyFFDDIHybrid;
import edu.uwo.csd.dcsim.projects.centralized.policies.VmPlacementPolicyFFMHybrid;
import edu.uwo.csd.dcsim.projects.centralized.policies.VmRelocationPolicyFFIMDHybrid;

public class BasicAutoscalingWithReallocationExperiment extends SimulationTask {

	private static Logger logger = Logger.getLogger(BasicAutoscalingWithReallocationExperiment.class);
	
	private static final long DURATION = SimTime.days(6);
//	private static final long DURATION = SimTime.minutes(5);
	private static final long METRIC_RECORD_START = SimTime.days(1);
	
	public static void main(String args[]) {
		Simulation.initializeLogging();
		
		//broadcast
		List<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
		
		executor.addTask(new BasicAutoscalingWithReallocationExperiment("autoscaling-allocation-1", 6198910678692541341l));
		executor.addTask(new BasicAutoscalingWithReallocationExperiment("autoscaling-allocation-2", 5646441053220106016l));
		executor.addTask(new BasicAutoscalingWithReallocationExperiment("autoscaling-allocation-3", -5705302823151233610l));
		executor.addTask(new BasicAutoscalingWithReallocationExperiment("autoscaling-allocation-4", 8289672009575825404l));
		executor.addTask(new BasicAutoscalingWithReallocationExperiment("autoscaling-allocation-5", -4637549055860880177l));
		executor.addTask(new BasicAutoscalingWithReallocationExperiment("autoscaling-allocation-6", -4280782692131378509l));
		executor.addTask(new BasicAutoscalingWithReallocationExperiment("autoscaling-allocation-7", -1699811527182374894l));
		executor.addTask(new BasicAutoscalingWithReallocationExperiment("autoscaling-allocation-8", -6452776964812569334l));
		executor.addTask(new BasicAutoscalingWithReallocationExperiment("autoscaling-allocation-9", -7148920787255940546l));
		executor.addTask(new BasicAutoscalingWithReallocationExperiment("autoscaling-allocation-10", 8311271444423629559l));		
		
		
//		completedTasks = executor.execute(); //execute all simulations simultaneously
		completedTasks = executor.execute(4); //execute 4 simulations (i.e. 4 threads) at a time
		
//		for(SimulationTask task : completedTasks) {
//			logger.info(task.getName());
//			task.getMetrics().printDefault(logger);
//		}
//		
		//output CSV
		for(SimulationTask task : completedTasks) {
			if (completedTasks.indexOf(task) == 0) {
				task.getMetrics().printCSV(System.out);
			} else {
				task.getMetrics().printCSV(System.out, false);
			}
		}

	}
	
	public BasicAutoscalingWithReallocationExperiment(String name) {
		super(name, DURATION);
		this.setMetricRecordStart(METRIC_RECORD_START);
	}
	
	public BasicAutoscalingWithReallocationExperiment(String name, long randomSeed) {
		super(name, DURATION);
		this.setMetricRecordStart(METRIC_RECORD_START);
		this.setRandomSeed(randomSeed);
	}

	@Override
	public void setup(Simulation simulation) {
		
		simulation.getSimulationMetrics().addCustomMetricCollection(new ApplicationManagementMetrics(simulation));
		
		Environment environment = new Environment(simulation, 40, 2);
		environment.createDataCentre(simulation);
			
		for (int i = 0; i < 20; ++i) {
			simulation.sendEvent(new ApplicationPlacementEvent(environment.getDcAM(), environment.createApplication()));
		}
	}
	
	public class Environment extends AppManagementTestEnvironment {

		HostPoolManager hostPool;
		
		public Environment(Simulation simulation, int hostsPerRack, int nRacks) {
			super(simulation, hostsPerRack, nRacks, new EqualShareLoadBalancer.Builder());
		}

		@Override
		public void processDcAM(AutonomicManager dcAM) {
			hostPool = new HostPoolManager();
			dcAM.addCapability(hostPool);
			
			// Set utilization thresholds.
			double lower = 0.60;
			double upper = 0.90;
			double target = 0.85;
			
			dcAM.installPolicy(new HostStatusPolicy(10));
			dcAM.installPolicy(new ApplicationPlacementPolicy(lower, upper, target));
			
			dcAM.installPolicy(new VmRelocationPolicyFFIMDHybrid(lower, upper, target), SimTime.minutes(10), SimTime.minutes(10) + 2);
			dcAM.installPolicy(new VmConsolidationPolicyFFDDIHybrid(lower, upper, target), SimTime.hours(1), SimTime.hours(1) + 3);
			
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
			AutonomicManager manager = new AutonomicManager(simulation);
			ApplicationManager applicationManager = new ApplicationManager(application, 5, 30);
			manager.addCapability(applicationManager);
			applicationManager.setAutonomicManager(manager);
			
			manager.installPolicy(new ApplicationScalingPolicy(dcAM, true), SimTime.minutes(5), 0);
			
			application.addApplicationListener(new ManagedApplicationListener(simulation, applicationManager));
		}
		
	}
	
	public class ManagedApplicationListener implements ApplicationListener {

		ApplicationManager applicationManager;
		Simulation simulation;
		
		public ManagedApplicationListener(Simulation simulation, ApplicationManager applicationManager) {
			this.simulation = simulation;
			this.applicationManager = applicationManager;
		}
		
		@Override
		public void onCreateTaskInstance(TaskInstance taskInstance) {
			AutonomicManager instanceManager = new AutonomicManager(simulation, new TaskInstanceManager(taskInstance));
			instanceManager.installPolicy(new TaskInstanceMonitoringPolicy(applicationManager), SimTime.minutes(1), simulation.getSimulationTime());
			
			applicationManager.addInstanceManager(taskInstance, instanceManager);
		}

		@Override
		public void onRemoveTaskInstance(TaskInstance taskInstance) {
			//shutdown the instance manager
			applicationManager.getInstanceManagers().get(taskInstance).shutdown();
			
			//remove the instance manager from the list
			applicationManager.removeInstanceManager(taskInstance);	
		}

		@Override
		public void onShutdownApplication(Application application) {
			// TODO Auto-generated method stub
			
		}
		
	}

}
