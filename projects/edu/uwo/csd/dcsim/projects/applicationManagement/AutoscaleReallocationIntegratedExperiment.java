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
import edu.uwo.csd.dcsim.application.loadbalancer.ShareLoadBalancer;
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
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.ApplicationPoolManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.TaskInstanceManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.policies.*;

public class AutoscaleReallocationIntegratedExperiment extends SimulationTask {

	private static Logger logger = Logger.getLogger(AutoscaleReallocationIntegratedExperiment.class);
	
	private static final long DURATION = SimTime.days(6);
//	private static final long DURATION = SimTime.minutes(5);
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
		
		//runSimulationSet(out, slaWarningThreshold, slaSafeThreshold, cpuSafeThreshold, upper, target, lower)
		
		//with SLA - SLA+ (true, 0.1, 0.1, 0.3, 0.9)
			//90 - 85 - 60
		runSimulationSet(printStream, 0.1, 0.1, 0.3, 0.9, 0.85, 0.6);
//			//90 - 85 - 50
//		runSimulationSet(printStream, 0.1, 0.1, 0.3, 0.9, 0.85, 0.5);
//			//85 - 80 - 50
//		runSimulationSet(printStream, 0.1, 0.1, 0.3, 0.85, 0.80, 0.5);
//		
//		//with SLA - Balanced (true, 0.5, 0.4, 0.3, 0.9)
//			//90 - 85 - 60
//		runSimulationSet(printStream, 0.5, 0.4, 0.3, 0.9, 0.85, 0.6);
//			//90 - 85 - 50
//		runSimulationSet(printStream, 0.5, 0.4, 0.3, 0.9, 0.85, 0.5);
//			//85 - 80 - 50
//		runSimulationSet(printStream, 0.5, 0.4, 0.3, 0.85, 0.8, 0.5);


//		List<SimulationTask> completedTasks;
//		SimulationExecutor executor = new SimulationExecutor();
		
//		executor.addTask(new AutoscaleReallocationIntegratedExperiment("autoscaling-integrated-1", 6198910678692541341l));
//		executor.addTask(new AutoscaleReallocationIntegratedExperiment("autoscaling-integrated-2", 5646441053220106016l));
//		executor.addTask(new AutoscaleReallocationIntegratedExperiment("autoscaling-integrated-3", -5705302823151233610l));
//		executor.addTask(new AutoscaleReallocationIntegratedExperiment("autoscaling-integrated-4", 8289672009575825404l));
//		executor.addTask(new AutoscaleReallocationIntegratedExperiment("autoscaling-integrated-5", -4637549055860880177l));
//		executor.addTask(new AutoscaleReallocationIntegratedExperiment("autoscaling-integrated-6", -4280782692131378509l));
//		executor.addTask(new AutoscaleReallocationIntegratedExperiment("autoscaling-integrated-7", -1699811527182374894l));
//		executor.addTask(new AutoscaleReallocationIntegratedExperiment("autoscaling-integrated-8", -6452776964812569334l));
//		executor.addTask(new AutoscaleReallocationIntegratedExperiment("autoscaling-integrated-9", -7148920787255940546l));
//		executor.addTask(new AutoscaleReallocationIntegratedExperiment("autoscaling-integrated-10", 8311271444423629559l));		
		
		
//		completedTasks = executor.execute(); //execute all simulations simultaneously
//		completedTasks = executor.execute(4); //execute 4 simulations (i.e. 4 threads) at a time
		
//		for(SimulationTask task : completedTasks) {
//			logger.info(task.getName());
//			task.getMetrics().printDefault(logger);
//		}
//		
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
			double cpuSafeThreshold,
			double upper,
			double target,
			double lower) {
		
		logger.info("Started New Simulation Set");
		logger.info(upper + "," + target + "," + lower + "," + slaWarningThreshold + "," + slaSafeThreshold + "," + "," + cpuSafeThreshold);
		
		List<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
		for (int i = 0; i < 1; ++i)  {
			AutoscaleReallocationIntegratedExperiment e = new AutoscaleReallocationIntegratedExperiment("integrated-" + (i + 1), randomSeeds[i]);
			e.setParameters(slaWarningThreshold, slaSafeThreshold, cpuSafeThreshold, upper, target, lower);
			executor.addTask(e);
		}
		
		completedTasks = executor.execute(6);
		
		for(SimulationTask task : completedTasks) {
			logger.info(task.getName());
			task.getMetrics().printDefault(logger);
		}
		
		//output CSV
		out.println("Autoscale+Reallocation Experiment");
		out.println("upper=" + upper + " | target=" + target + " | lower=" + lower +
				" | slaWarning=" + slaWarningThreshold + " | slaSafe=" + slaSafeThreshold + 
				" | cpuSafe=" + cpuSafeThreshold);
		
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
	
	public AutoscaleReallocationIntegratedExperiment(String name) {
		super(name, DURATION);
		this.setMetricRecordStart(METRIC_RECORD_START);
	}
	
	public AutoscaleReallocationIntegratedExperiment(String name, long randomSeed) {
		super(name, DURATION);
		this.setMetricRecordStart(METRIC_RECORD_START);
		this.setRandomSeed(randomSeed);
	}

	private double slaWarningThreshold = 0.8;
	private double slaSafeThreshold = 0.6;
	private long scaleDownFreeze = SimTime.minutes(60);
	private double cpuSafeThreshold = 0.5;
	private int shortWindow = 5;
	private int longWindow = 30;
	private long scalingInterval = SimTime.minutes(5);
	private double upper = 0.90;
	private double target = 0.85;
	private double lower = 0.60;
	
	public void setParameters(double slaWarningThreshold, 
			double slaSafeThreshold,
			double cpuSafeThreshold,
			double upper,
			double target,
			double lower) {
		
		this.slaWarningThreshold = slaWarningThreshold;
		this.slaSafeThreshold = slaSafeThreshold;
		this.cpuSafeThreshold = cpuSafeThreshold;
		this.upper = upper;
		this.target = target;
		this.lower = lower;
		
	}
	
	@Override
	public void setup(Simulation simulation) {
		
		simulation.getSimulationMetrics().addCustomMetricCollection(new ApplicationManagementMetrics(simulation));
		
		Environment environment = new Environment(simulation, 30, 4);
		environment.createDataCentre(simulation);
		
		ArrayList<Application> applications = new ArrayList<Application>();
		for (int i = 0; i < 50; ++i) {
			applications.add(environment.createApplication());
		}
		simulation.sendEvent(new ApplicationPlacementEvent(environment.getDcAM(), applications));
	}
	
	public class Environment extends AppManagementTestEnvironment {

		HostPoolManager hostPool;
		ApplicationPoolManager applicationPool;
		
		public Environment(Simulation simulation, int hostsPerRack, int nRacks) {
			super(simulation, hostsPerRack, nRacks, new ShareLoadBalancer.Builder());
		}

		@Override
		public void processDcAM(AutonomicManager dcAM) {
			
			hostPool = new HostPoolManager();
			dcAM.addCapability(hostPool);
			hostPool.setAutonomicManager(dcAM);
			applicationPool = new ApplicationPoolManager(shortWindow, longWindow);
			dcAM.addCapability(applicationPool);
			applicationPool.setAutonomicManager(dcAM);
			
			dcAM.installPolicy(new HostStatusPolicy(10));
			dcAM.installPolicy(new ApplicationPlacementPolicy(lower, upper, target));
			
			ApplicationManagementPolicy appManagementPolicy = new ApplicationManagementPolicy(lower, upper, target);
			appManagementPolicy.setParameters(slaWarningThreshold, slaSafeThreshold, scaleDownFreeze, cpuSafeThreshold);
			dcAM.installPolicy(appManagementPolicy, scalingInterval, 0);
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
			applicationPool.addApplication(application);
			
			application.addApplicationListener(new ManagedApplicationListener(simulation, applicationPool));
		}
		
	}
	
	public class ManagedApplicationListener implements ApplicationListener {

		ApplicationPoolManager applicationPoolManager;
		Simulation simulation;
		
		public ManagedApplicationListener(Simulation simulation, ApplicationPoolManager applicationPoolManager) {
			this.simulation = simulation;
			this.applicationPoolManager = applicationPoolManager;
		}
		
		@Override
		public void onCreateTaskInstance(TaskInstance taskInstance) {
			AutonomicManager instanceManager = new AutonomicManager(simulation, new TaskInstanceManager(taskInstance));
			instanceManager.installPolicy(new TaskInstanceMonitoringPolicy(applicationPoolManager), SimTime.minutes(1), simulation.getSimulationTime());
			
			applicationPoolManager.getApplicationData(taskInstance.getTask().getApplication()).addInstanceManager(taskInstance, instanceManager);
		}

		@Override
		public void onRemoveTaskInstance(TaskInstance taskInstance) {
			//shutdown the instance manager
			
			applicationPoolManager.getApplicationData(taskInstance.getTask().getApplication()).getInstanceManagers().get(taskInstance).shutdown();
			
			//remove the instance manager from the list
			applicationPoolManager.getApplicationData(taskInstance.getTask().getApplication()).removeInstanceManager(taskInstance);	
		}

		@Override
		public void onShutdownApplication(Application application) {
			applicationPoolManager.removeApplication(application);
		}
		
	}

}
