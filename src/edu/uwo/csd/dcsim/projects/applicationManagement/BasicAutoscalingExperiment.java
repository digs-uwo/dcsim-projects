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
import edu.uwo.csd.dcsim.application.VmmApplication;
import edu.uwo.csd.dcsim.application.loadbalancer.EqualShareLoadBalancer;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Cluster;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Rack;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.capabilities.*;
import edu.uwo.csd.dcsim.management.events.ApplicationPlacementEvent;
import edu.uwo.csd.dcsim.management.policies.*;
import edu.uwo.csd.dcsim.projects.applicationManagement.ApplicationManagementExperiment.Environment;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.ApplicationManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.TaskInstanceManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.policies.*;

public class BasicAutoscalingExperiment extends SimulationTask {

	private static Logger logger = Logger.getLogger(BasicAutoscalingExperiment.class);
	
	private static final long RAMP_UP_TIME = SimTime.hours(20); //20
	private static final long APP_ARRIVAL_START_TIME =  SimTime.hours(24); //24
	private static final long DURATION = SimTime.days(8); //8
	private static final long METRIC_RECORD_START = SimTime.hours(24); //24
	
	private static final int RACK_SIZE = 40; //40
	private static final int N_RACKS = 5; //5
	private static final int N_APPS_MAX = 40; //40
	private static final int N_APPS_MIN = 10; //10
	private static final double CHANGES_PER_DAY = 1; //0.5
	private static final boolean DYNAMIC_ARRIVALS = true;
	
	private static final boolean STATIC_FULL = false; //if True, applications will be statically allocated their full size, no autoscaling will occur
	
	private static final boolean CSV_OUTPUT = false;
	
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
	private static final long N_SEEDS = 1;

	public static void main(String args[]) {
		Simulation.initializeLogging();
		
		PrintStream printStream;
		try {
			printStream = new PrintStream("out_autoscaling");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		
		/*
		 * SLA AWARE
		 */

		/*
		 * NOMS 2014 Autoscaling Settings 
		 */
//		runSimulationSet(printStream, 0.3, 0.2, SimTime.minutes(60), 0.3, 5, 30, SimTime.minutes(5)); //SLA - LOWER CPU SAFE 
		
		/*
		 * NOMS 2014 Extended
		 */
		//runSimulationSet(out,slaWarningThreshold,slaSafeThreshold,scaleDownFreeze, cpuSafeThreshold, shortWindow, longWindow, scalingInterval)
//		runSimulationSet(printStream, 0.25, 0.2, SimTime.minutes(60), 0.3, 5, 30, SimTime.minutes(5)); //lower SLA warning
//		runSimulationSet(printStream, 0.3, 0.15, SimTime.minutes(60), 0.3, 5, 30, SimTime.minutes(5)); //lower SLA safe
//		runSimulationSet(printStream, 0.25, 0.15, SimTime.minutes(60), 0.3, 5, 30, SimTime.minutes(5)); //lower SLA warning & safe
//		runSimulationSet(printStream, 0.3, 0.2, SimTime.minutes(60), 0.2, 5, 30, SimTime.minutes(5)); //lower CPU safe
		runSimulationSet(printStream, 0.2, 0.15, SimTime.minutes(60), 0.3, 5, 30, SimTime.minutes(5)); //lower SLA warning
		
//		runSimulationSet(printStream, 0.9, 0.6, SimTime.minutes(60), 0.3, 5, 30, SimTime.minutes(5)); 
//		runSimulationSet(printStream, 0.6, 0.6, SimTime.minutes(60), 0.3, 5, 30, SimTime.minutes(5)); 
//		runSimulationSet(printStream, 0.6, 0.6, SimTime.minutes(60), 0.5, 5, 30, SimTime.minutes(5)); 
//		runSimulationSet(printStream, 0.2, 0.6, SimTime.minutes(60), 0.3, 5, 30, SimTime.minutes(5)); 
//		runSimulationSet(printStream, 0.8, 0.6, SimTime.minutes(30), 0.5, 5, 30, SimTime.minutes(5)); 
//		runSimulationSet(printStream, 0.8, 0.4, SimTime.minutes(30), 0.3, 5, 30, SimTime.minutes(5)); 
		
		
		printStream.println("DONE!");
		printStream.flush();
		printStream.close();


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
		logger.info(slaWarningThreshold + "," + slaSafeThreshold + "," + SimTime.toMinutes(scaleDownFreeze) + "," + cpuSafeThreshold + 
				"," + shortWindow + "," + longWindow + "," + SimTime.toMinutes(scalingInterval));
		
		List<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
		for (int i = 0; i < N_SEEDS; ++i)  {
			BasicAutoscalingExperiment e = new BasicAutoscalingExperiment("autoscaling-" + (i + 1), randomSeeds[i]);
			e.setParameters(slaWarningThreshold, slaSafeThreshold, scaleDownFreeze, cpuSafeThreshold, shortWindow, longWindow, scalingInterval);
			executor.addTask(e);
		}
		
		completedTasks = executor.execute(6);
		
		if (CSV_OUTPUT) {
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
		} else {
			for(SimulationTask task : completedTasks) {
				logger.info(task.getName());
				task.getMetrics().printDefault(logger);
			}
		}
		
		
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
		
		Environment environment = new Environment(simulation, RACK_SIZE, N_RACKS);
		environment.createDataCentre(simulation);
		
		if(DYNAMIC_ARRIVALS) {
			//change level every 2 days, min 10 apps, max 50 apps, ramp up 20 hours, start random at 24 hours, duration 2 days
			environment.configureRandomApplications(simulation, CHANGES_PER_DAY, N_APPS_MIN, N_APPS_MAX, RAMP_UP_TIME, APP_ARRIVAL_START_TIME, DURATION, STATIC_FULL);
		} else {
			environment.configureStaticApplications(simulation, N_APPS_MAX, STATIC_FULL);
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
			if (!STATIC_FULL) {
				AutonomicManager manager = new AutonomicManager(simulation);
				ApplicationManager applicationManager = new ApplicationManager(application, shortWindow, longWindow);
				manager.addCapability(applicationManager);
				applicationManager.setAutonomicManager(manager);
				
				ApplicationScalingPolicy appPolicy = new ApplicationScalingPolicy(dcAM);
				appPolicy.setParameters(slaWarningThreshold, slaSafeThreshold, scaleDownFreeze, cpuSafeThreshold);
				manager.installPolicy(appPolicy, scalingInterval, simulation.getSimulationTime());
				
				application.addApplicationListener(new ManagedApplicationListener(simulation, applicationManager));
			}
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
			applicationManager.getManager().shutdown();
		}
		
	}

}

