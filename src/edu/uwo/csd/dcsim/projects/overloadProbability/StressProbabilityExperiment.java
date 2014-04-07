package edu.uwo.csd.dcsim.projects.overloadProbability;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.List;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.SimulationExecutor;
import edu.uwo.csd.dcsim.SimulationTask;
import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.application.loadbalancer.EqualShareLoadBalancer;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Cluster;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Rack;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.capabilities.HostManager;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.management.policies.HostMonitoringPolicy;
import edu.uwo.csd.dcsim.management.policies.HostOperationsPolicy;
import edu.uwo.csd.dcsim.management.policies.HostStatusPolicy;
import edu.uwo.csd.dcsim.projects.applicationManagement.policies.ApplicationPlacementPolicy;
import edu.uwo.csd.dcsim.projects.centralized.policies.VmConsolidationPolicyFFDDIHybrid;
import edu.uwo.csd.dcsim.projects.centralized.policies.VmRelocationPolicyFFIMDHybrid;
import edu.uwo.csd.dcsim.projects.overloadProbability.capabilities.VmMarkovChainManager;
import edu.uwo.csd.dcsim.projects.overloadProbability.policies.*;

public class StressProbabilityExperiment extends SimulationTask {

	private static Logger logger = Logger.getLogger(StressProbabilityExperiment.class);
	
	private static final long RAMP_UP_TIME = SimTime.hours(20); //20
	private static final long APP_ARRIVAL_START_TIME =  SimTime.hours(24); //24
	private static final long DURATION = SimTime.days(8); //8
	private static final long METRIC_RECORD_START = SimTime.hours(24); //24
	
	private static final int RACK_SIZE = 40; //40
	private static final int N_RACKS = 8; //8
	private static final int N_APPS_MAX = 500; //50
	private static final int N_APPS_MIN = 500; //10
	private static final double CHANGES_PER_DAY = 0.5; //0.5
	private static final boolean DYNAMIC_ARRIVALS = false; //true
		
	private static final boolean CSV_OUTPUT = false;
	
	private static final boolean STRESS_PROBABILITY = true;
	
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
	private static final long N_SEEDS = 1; //10

	/*
	 * Main
	 */
	public static void main(String args[]) {
		Simulation.initializeLogging();
		
		PrintStream printStream;
		try {
			printStream = new PrintStream("out_stress_prob");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		
		System.out.println("Starting StressProbabilityExperiment");
		
		runSimulationSet(printStream, 0.8, 0.75, 0.4, 0.3);
	
		printStream.println("Done");
		printStream.close();
		
	}
	
	public static void runSimulationSet(PrintStream out,
			double upper,
			double target,
			double lower,
			double pThreshold) {
		
		logger.info("Started New Simulation Set");
		logger.info(upper + "," + target + "," + lower);
		
		List<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
		for (int i = 0; i < N_SEEDS; ++i)  {
			StressProbabilityExperiment e = new StressProbabilityExperiment("stress-probability-" + (i + 1), randomSeeds[i]);
			e.setParameters(upper, target, lower, pThreshold);
			executor.addTask(e);
		}
		
		completedTasks = executor.execute(4);
		
		if (CSV_OUTPUT) {
			//output CSV
			out.println("Autoscale+Reallocation Experiment");
			out.println("upper=" + upper + " | target=" + target + " | lower=" + lower);
			
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
	
	
	/*
	 * Experiment Class
	 */
	
	private double upper = 0.90;
	private double target = 0.85;
	private double lower = 0.60;
	private double pThreshold = 0.2;
	
	public StressProbabilityExperiment(String name, long randomSeed) {
		super(name, DURATION);
		this.setMetricRecordStart(METRIC_RECORD_START);
		this.setRandomSeed(randomSeed);
	}
	
	public void setParameters(
			double upper,
			double target,
			double lower,
			double pThreshold) {
		
		this.upper = upper;
		this.target = target;
		this.lower = lower;
		this.pThreshold = pThreshold;
	}
	
	@Override
	public void setup(Simulation simulation) {
			
		Environment environment = new Environment(simulation, RACK_SIZE, N_RACKS);
		environment.createDataCentre(simulation);
		
		if(DYNAMIC_ARRIVALS) {
			//change level every 2 days, min 10 apps, max 50 apps, ramp up 20 hours, start random at 24 hours, duration 2 days
			environment.configureRandomApplications(simulation, CHANGES_PER_DAY, N_APPS_MIN, N_APPS_MAX, RAMP_UP_TIME, APP_ARRIVAL_START_TIME, DURATION);
		} else {
			environment.configureStaticApplications(simulation, N_APPS_MAX);
		}
	}
	
	public class Environment extends StressProbabilityEnvironment {

		HostPoolManager hostPool;
		VmMarkovChainManager mcMan;
		
		
		public Environment(Simulation simulation, int hostsPerRack, int nRacks) {
			super(simulation, hostsPerRack, nRacks, new EqualShareLoadBalancer.Builder());
		}

		@Override
		public void processDcAM(AutonomicManager dcAM) {
			hostPool = new HostPoolManager();
			dcAM.addCapability(hostPool);
			mcMan = new VmMarkovChainManager(upper);
			dcAM.addCapability(mcMan);
			
			dcAM.installPolicy(new HostStatusPolicy(10));
			dcAM.installPolicy(new ApplicationPlacementPolicy(lower, upper, target));
			dcAM.installPolicy(new VmMarkovChainUpdatePolicy());
			
			//NOTE, we are starting a day late to give the markov chains time to collect data
			if (STRESS_PROBABILITY) {
				dcAM.installPolicy(new VmRelocationPolicyProbability(lower, upper, target, pThreshold), SimTime.minutes(10), SimTime.days(1) + SimTime.minutes(20) + 2);
				dcAM.installPolicy(new VmConsolidationPolicyProbability(lower, upper, target), SimTime.hours(1), SimTime.days(1) + SimTime.hours(1) + 3);
			} else {
				dcAM.installPolicy(new VmRelocationPolicyFFIMDHybrid(lower, upper, target), SimTime.minutes(10), SimTime.days(1) + SimTime.minutes(20) + 2);
				dcAM.installPolicy(new VmConsolidationPolicyFFDDIHybrid(lower, upper, target), SimTime.hours(1), SimTime.days(1) + SimTime.hours(1) + 3);
			}
			
		}

		@Override
		public void processHost(Host host, Rack rack, Cluster cluster,
				DataCentre dc, AutonomicManager dcAM) {
			
			AutonomicManager hostAM = new AutonomicManager(simulation, new HostManager(host));
			hostAM.installPolicy(new HostMonitoringPolicy(dcAM), SimTime.minutes(5), SimTime.minutes(envRandom.nextInt(5)));
			hostAM.installPolicy(new HostOperationsPolicy());

			host.installAutonomicManager(hostAM);
			
			hostPool.addHost(host, hostAM);
		}

		@Override
		public void processApplication(InteractiveApplication application) {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	
}
