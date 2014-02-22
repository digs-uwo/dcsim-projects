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
import edu.uwo.csd.dcsim.projects.applicationManagement.ApplicationManagementExperiment.Environment;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.ApplicationManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.TaskInstanceManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.policies.*;
import edu.uwo.csd.dcsim.projects.centralized.policies.VmConsolidationPolicyFFDDIHybrid;
import edu.uwo.csd.dcsim.projects.centralized.policies.VmRelocationPolicyFFIMDHybrid;

public class BasicAutoscalingWithReallocationExperiment extends SimulationTask {

	private static Logger logger = Logger.getLogger(BasicAutoscalingWithReallocationExperiment.class);
	
	private static final long RAMP_UP_TIME = SimTime.hours(20); //20
	private static final long APP_ARRIVAL_START_TIME =  SimTime.hours(24); //24
	private static final long DURATION = SimTime.days(8); //8
	private static final long METRIC_RECORD_START = SimTime.hours(24); //24
	
	private static final int RACK_SIZE = 40; //40
	private static final int N_RACKS = 8; //8
	private static final int N_APPS_MAX = 50; //50
	private static final int N_APPS_MIN = 10; //10
	private static final double CHANGES_PER_DAY = 0.5; //0.5
	private static final boolean DYNAMIC_ARRIVALS = true; //true
		
	private static final boolean CSV_OUTPUT = true;
	
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
	private static final long N_SEEDS = 10; //10
	
	public static void main(String args[]) {
		Simulation.initializeLogging();
		
		PrintStream printStream;
		try {
			printStream = new PrintStream("out_seperate");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		
		//use 10 minute relocation and 1 hour consolidation, as determined in previous work... try longer consolidation?
		System.out.println("Starting BasicAutoscalingWithReallocationExperiment");
		
		//runSimulationSet(out, slaWarningThreshold, slaSafeThreshold, cpuSafeThreshold, cpuWarningThreshold, upper, target, lower)
		
		//with SLA (0.3, 0.2, 0.3)
			//90 - 85 - 60
		runSimulationSet(printStream, 0.3, 0.2, 0.3, 0.9, 0.85, 0.6);
			//90 - 85 - 50
		runSimulationSet(printStream, 0.3, 0.2, 0.3, 0.9, 0.85, 0.5);
			//90 - 85 - 40
		runSimulationSet(printStream, 0.3, 0.2, 0.3, 0.9, 0.85, 0.4);
			//85 - 80 - 60
		runSimulationSet(printStream, 0.3, 0.2, 0.3, 0.85, 0.8, 0.6);
			//85 - 80 - 50
		runSimulationSet(printStream, 0.3, 0.2, 0.3, 0.85, 0.8, 0.5);
			//85 - 80 - 40
		runSimulationSet(printStream, 0.3, 0.2, 0.3, 0.85, 0.8, 0.4);
			//80 - 75 - 60
		runSimulationSet(printStream, 0.3, 0.2, 0.3, 0.8, 0.75, 0.6);
			//80 - 75 - 50
		runSimulationSet(printStream, 0.3, 0.2, 0.3, 0.8, 0.75, 0.5);
			//80 - 75 - 40
		runSimulationSet(printStream, 0.3, 0.2, 0.3, 0.8, 0.75, 0.4);
			//75 - 70 - 60
		runSimulationSet(printStream, 0.3, 0.2, 0.3, 0.75, 0.7, 0.6);
			//75 - 70 - 50
		runSimulationSet(printStream, 0.3, 0.2, 0.3, 0.75, 0.7, 0.5);
			//75 - 70 - 40
		runSimulationSet(printStream, 0.3, 0.2, 0.3, 0.75, 0.7, 0.4);
		
	
		
		printStream.println("Done");
		printStream.close();
		
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
		for (int i = 0; i < N_SEEDS; ++i)  {
			BasicAutoscalingWithReallocationExperiment e = new BasicAutoscalingWithReallocationExperiment("autoscaling-reallocation-" + (i + 1), randomSeeds[i]);
			e.setParameters(slaWarningThreshold, slaSafeThreshold, cpuSafeThreshold, upper, target, lower);
			executor.addTask(e);
		}
		
		completedTasks = executor.execute(4);
		
		if (CSV_OUTPUT) {
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
		} else {
			for(SimulationTask task : completedTasks) {
				logger.info(task.getName());
				task.getMetrics().printDefault(logger);
			}
		}
		
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
	
	public BasicAutoscalingWithReallocationExperiment(String name) {
		super(name, DURATION);
		this.setMetricRecordStart(METRIC_RECORD_START);
	}
	
	public BasicAutoscalingWithReallocationExperiment(String name, long randomSeed) {
		super(name, DURATION);
		this.setMetricRecordStart(METRIC_RECORD_START);
		this.setRandomSeed(randomSeed);
	}
	
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
		
		Environment environment = new Environment(simulation, RACK_SIZE, N_RACKS);
		environment.createDataCentre(simulation);
		
		if(DYNAMIC_ARRIVALS) {
			//change level every 2 days, min 10 apps, max 50 apps, ramp up 20 hours, start random at 24 hours, duration 2 days
			environment.configureRandomApplications(simulation, CHANGES_PER_DAY, N_APPS_MIN, N_APPS_MAX, RAMP_UP_TIME, APP_ARRIVAL_START_TIME, DURATION);
		} else {
			environment.configureStaticApplications(simulation, N_APPS_MAX);
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
			dcAM.installPolicy(new ApplicationPlacementPolicy(lower, upper, target));
			
			dcAM.installPolicy(new VmRelocationPolicyFFIMDHybrid(lower, upper, target), SimTime.minutes(10), SimTime.minutes(20) + 2);
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
			ApplicationManager applicationManager = new ApplicationManager(application, shortWindow, longWindow);
			manager.addCapability(applicationManager);
			applicationManager.setAutonomicManager(manager);
			
			ApplicationScalingPolicy appPolicy = new ApplicationScalingPolicy(dcAM);
			appPolicy.setParameters(slaWarningThreshold, slaSafeThreshold, scaleDownFreeze, cpuSafeThreshold);
			manager.installPolicy(appPolicy, scalingInterval, simulation.getSimulationTime());
			
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
			applicationManager.getManager().shutdown();
		}
		
	}

}
