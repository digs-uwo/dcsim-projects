package edu.uwo.csd.dcsim.projects.applicationManagement;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
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

public class BasicAutoscalingExperiment extends SimulationTask {

	private static Logger logger = Logger.getLogger(BasicAutoscalingExperiment.class);
	
	private static final long DURATION = SimTime.days(8);
	private static final long METRIC_RECORD_START = SimTime.days(1);
	
	private static final long[] randomSeeds = {6198910678692541341l,
		5646441053220106016l,
		-5705302823151233610l,
		8289672009575825404l,
		-4637549055860880177l,
		-4280782692131378509l,
		-1699811527182374894l,
		-6452776964812569334l,
		-7148920787255940546l,
		8311271444423629559l};

	public static void main(String args[]) {
		Simulation.initializeLogging();
		
		PrintStream printStream;
		try {
			printStream = new PrintStream("out");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		
		/*
		 * SLA AWARE
		 */
		
//		//autoscaling interval
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(10));
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(15));
//
//		//adjust sla warning
//		runSimulationSet(printStream, true, 0.9, 0.6, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.7, 0.6, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.6, 0.6, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.5, 0.4, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.4, 0.3, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.3, 0.2, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//
//		//adjust sla safe
////		runSimulationSet(printStream, true, 0.8, 0.7, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
////		runSimulationSet(printStream, true, 0.8, 0.5, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.8, 0.4, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		
//		//adjust cpu safe
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.6, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.4, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.3, 0.9, 5, 30, SimTime.minutes(5));
//		
//		//adjust sla safe with cpu safe
////		runSimulationSet(printStream, true, 0.8, 0.7, SimTime.minutes(30), 0.6, 0.9, 5, 30, SimTime.minutes(5));
////		runSimulationSet(printStream, true, 0.8, 0.5, SimTime.minutes(30), 0.4, 0.9, 5, 30, SimTime.minutes(5));
////		runSimulationSet(printStream, true, 0.8, 0.4, SimTime.minutes(30), 0.3, 0.9, 5, 30, SimTime.minutes(5));
//		
//		//both sla high
////		runSimulationSet(printStream, true, 0.9, 0.7, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		
//		//both sla low
////		runSimulationSet(printStream, true, 0.6, 0.4, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		
//		//sla split high low
////		runSimulationSet(printStream, true, 0.9, 0.4, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
////		runSimulationSet(printStream, true, 0.6, 0.6, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		
//		//adjust scale down freeze
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(5), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(10), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(20), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(45), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(60), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		
//		//adjust window sizes
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.5, 0.9, 1, 1, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.5, 0.9, 5, 10, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.5, 0.9, 5, 20, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.5, 0.9, 10, 5, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.5, 0.9, 10, 20, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.5, 0.9, 10, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.5, 0.9, 20, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.5, 0.9, 30, 30, SimTime.minutes(5));
//		
//		
//		//sla warn high, CPU safe high
//		runSimulationSet(printStream, true, 0.9, 0.6, SimTime.minutes(30), 0.6, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.9, 0.6, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		
//		//sla warn high, CPU safe low
//		runSimulationSet(printStream, true, 0.9, 0.6, SimTime.minutes(30), 0.3, 0.9, 5, 30, SimTime.minutes(5));
//		
//		//sla warn low, CPU safe high
//		runSimulationSet(printStream, true, 0.6, 0.6, SimTime.minutes(30), 0.6, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.6, 0.6, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.3, 0.6, SimTime.minutes(30), 0.6, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.3, 0.6, SimTime.minutes(30), 0.5, 0.9, 5, 30, SimTime.minutes(5));
//		
//		//sla warn low, CPU safe low
//		runSimulationSet(printStream, true, 0.6, 0.6, SimTime.minutes(30), 0.3, 0.9, 5, 30, SimTime.minutes(5));
//		
//		//sla warn very low, CPU safe low
////		runSimulationSet(printStream, true, 0.5, 0.6, SimTime.minutes(60), 0.3, 0.9, 5, 30, SimTime.minutes(5));
////		runSimulationSet(printStream, true, 0.4, 0.6, SimTime.minutes(60), 0.3, 0.9, 5, 30, SimTime.minutes(5));
////		runSimulationSet(printStream, true, 0.3, 0.6, SimTime.minutes(60), 0.3, 0.9, 5, 30, SimTime.minutes(5));
//		runSimulationSet(printStream, true, 0.2, 0.6, SimTime.minutes(60), 0.3, 0.9, 5, 30, SimTime.minutes(5));		
//		
//		//adjust cpu safe very high
////		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.7, 0.9, 5, 30, SimTime.minutes(5));
////		runSimulationSet(printStream, true, 0.3, 0.2, SimTime.minutes(30), 0.7, 0.9, 5, 30, SimTime.minutes(5));
////		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.8, 0.9, 5, 30, SimTime.minutes(5));
////		runSimulationSet(printStream, true, 0.3, 0.2, SimTime.minutes(30), 0.8, 0.9, 5, 30, SimTime.minutes(5));
////		runSimulationSet(printStream, true, 0.8, 0.6, SimTime.minutes(30), 0.9, 0.9, 5, 30, SimTime.minutes(5));
////		runSimulationSet(printStream, true, 0.3, 0.2, SimTime.minutes(30), 0.9, 0.9, 5, 30, SimTime.minutes(5));
		
		//TODO:
		//use long scale down freeze (60 minutes)
		
		//runSimulationSet(out,slaWarningThreshold,slaSafeThreshold,scaleDownFreeze,cpuSafeThreshold, shortWindow, longWindow, scalingInterval)
		
		//Try "best" configurations
//		runSimulationSet(printStream, true, 0.1, 0.1, SimTime.minutes(60), 0.3, 0.9, 5, 30, SimTime.minutes(5)); //SLA SLA+
//		runSimulationSet(printStream, true, 0.2, 0.1, SimTime.minutes(60), 0.3, 0.9, 5, 30, SimTime.minutes(5)); //SLA SLA
//		runSimulationSet(printStream, true, 0.9, 0.4, SimTime.minutes(60), 0.3, 0.9, 5, 30, SimTime.minutes(5)); //SLA Operations
//		runSimulationSet(printStream, true, 0.9, 0.4, SimTime.minutes(60), 0.6, 0.9, 5, 30, SimTime.minutes(5)); //SLA Power
//		runSimulationSet(printStream, true, 0.5, 0.4, SimTime.minutes(60), 0.3, 0.9, 5, 30, SimTime.minutes(5)); //SLA Balanced
//		runSimulationSet(printStream, true, 0.5, 0.4, SimTime.minutes(60), 0.6, 0.9, 5, 30, SimTime.minutes(5)); //SLA New Balanced
		
		
//		runSimulationSet(printStream, 0.3, 0.2, SimTime.minutes(60), 0.5, 5, 30, SimTime.minutes(5)); //SLA
		
		/*
		 * NOMS 2014 Autoscaling Settings 
		 */
		runSimulationSet(printStream, 0.3, 0.2, SimTime.minutes(60), 0.3, 5, 30, SimTime.minutes(5)); //SLA - LOWER CPU SAFE 
		
		printStream.println("DONE!");
		printStream.flush();
		printStream.close();
		
//		List<SimulationTask> completedTasks;
//		SimulationExecutor executor = new SimulationExecutor();
//		
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-1", 6198910678692541341l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-2", 5646441053220106016l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-3", -5705302823151233610l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-4", 8289672009575825404l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-5", -4637549055860880177l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-6", -4280782692131378509l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-7", -1699811527182374894l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-8", -6452776964812569334l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-9", -7148920787255940546l));
//		executor.addTask(new BasicAutoscalingExperiment("autoscaling-10", 8311271444423629559l));		
		
		
//		completedTasks = executor.execute(); //execute all simulations simultaneously
//		completedTasks = executor.execute(4); //execute 4 simulations (i.e. 4 threads) at a time
		
//		for(SimulationTask task : completedTasks) {
//			logger.info(task.getName());
//			task.getMetrics().printDefault(logger);
//		}
		
		//output CSV
//		for(SimulationTask task : completedTasks) {
//			if (completedTasks.indexOf(task) == 0) {
//				task.getMetrics().printCSV(System.out);
//			} else {
//				task.getMetrics().printCSV(System.out, false);
//			}
//		}

	}
	
	public static void runSimulationSet(PrintStream out, 
			double slaWarningThreshold, 
			double slaSafeThreshold,
			long scaleDownFreeze,
			double cpuSafeThreshold,
			int shortWindow,
			int longWindow,
			long scalingInterval) {
		
		logger.info("Started New Simulation Set");
		logger.info(slaWarningThreshold + "," + slaSafeThreshold + "," + SimTime.toMinutes(scaleDownFreeze) + "," + cpuSafeThreshold + "," +
				"," + shortWindow + "," + longWindow + "," + SimTime.toMinutes(scalingInterval));
		
		List<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
		for (int i = 0; i < 10; ++i)  {
			BasicAutoscalingExperiment e = new BasicAutoscalingExperiment("autoscaling-" + (i + 1), randomSeeds[i]);
			e.setParameters(slaWarningThreshold, slaSafeThreshold, scaleDownFreeze, cpuSafeThreshold, shortWindow, longWindow, scalingInterval);
			executor.addTask(e);
		}
		
		completedTasks = executor.execute(6);
		
		//output CSV
		out.println("Autoscale Experiment");
		out.println("slaWarningThreshold, slaSafeThreshold, scaleDownFreeze, cpuSafeThreshold, shortWindow, longWindow, scalingInterval");
		out.println(slaWarningThreshold + "," + slaSafeThreshold + "," + SimTime.toMinutes(scaleDownFreeze) + "," + cpuSafeThreshold + "," +
				shortWindow + "," + longWindow + "," + SimTime.toMinutes(scalingInterval));
		for(SimulationTask task : completedTasks) {
			if (completedTasks.indexOf(task) == 0) {
				task.getMetrics().printCSV(out);
			} else {
				task.getMetrics().printCSV(out, false);
			}
		}
		out.println("");
		out.println("");
		
		out.flush();
		
	}
	
	private double slaWarningThreshold = 0.8;
	private double slaSafeThreshold = 0.6;
	private long scaleDownFreeze = SimTime.minutes(30);
	private double cpuSafeThreshold = 0.5;
	private int shortWindow = 5; //small window for sliding average response time/cpu util
	private int longWindow = 30; //large window for sliding average response time/cpu util
	private long scalingInterval = SimTime.minutes(5);
	
	public BasicAutoscalingExperiment(String name) {
		super(name, DURATION);
		this.setMetricRecordStart(METRIC_RECORD_START);
	}
	
	public BasicAutoscalingExperiment(String name, long randomSeed) {
		super(name, DURATION);
		this.setMetricRecordStart(METRIC_RECORD_START);
		this.setRandomSeed(randomSeed);
	}

	public void setParameters(double slaWarningThreshold, 
			double slaSafeThreshold,
			long scaleDownFreeze,
			double cpuSafeThreshold,
			int shortWindow,
			int longWindow,
			long scalingInterval) {
		
		this.slaWarningThreshold = slaWarningThreshold;
		this.slaSafeThreshold = slaSafeThreshold;
		this.scaleDownFreeze = scaleDownFreeze;
		this.cpuSafeThreshold = cpuSafeThreshold;
		this.shortWindow = shortWindow;
		this.longWindow = longWindow;
		this.scalingInterval = scalingInterval;
		
	}
	
	@Override
	public void setup(Simulation simulation) {
		
		simulation.getSimulationMetrics().addCustomMetricCollection(new ApplicationManagementMetrics(simulation));
		
		Environment environment = new Environment(simulation, 40, 5); //40 hosts, 5 racks
		environment.createDataCentre(simulation);
		
		ArrayList<Application> applications = new ArrayList<Application>();
		for (int i = 0; i < 50; ++i) {
			applications.add(environment.createApplication());
		}
		simulation.sendEvent(new ApplicationPlacementEvent(environment.getDcAM(), applications));
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
			
			dcAM.installPolicy(new HostStatusPolicy(10));
			dcAM.installPolicy(new BasicApplicationPlacementPolicy());
			
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
			ApplicationManager applicationManager = new ApplicationManager(application, shortWindow, longWindow);
			manager.addCapability(applicationManager);
			applicationManager.setAutonomicManager(manager);
			
			ApplicationScalingPolicy appPolicy = new ApplicationScalingPolicy(dcAM);
			appPolicy.setParameters(slaWarningThreshold, slaSafeThreshold, scaleDownFreeze, cpuSafeThreshold);
			manager.installPolicy(appPolicy, scalingInterval, 0);
			
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
