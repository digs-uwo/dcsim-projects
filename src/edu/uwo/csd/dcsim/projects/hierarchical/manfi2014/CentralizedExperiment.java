package edu.uwo.csd.dcsim.projects.hierarchical.manfi2014;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.List;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.common.*;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.capabilities.*;
import edu.uwo.csd.dcsim.management.policies.*;
import edu.uwo.csd.dcsim.projects.centralized.policies.*;

/**
 * This class serves to evaluate the Centralized Management System in the test  
 * environment for the ManFI 2014 paper. The strategy in use is Reactive Hybrid 
 * and consists of the following policies:
 * 
 * + VmPlacementPolicyFFMHybrid
 * + VmRelocationPolicyHybridReactive
 * + VmConsolidationPolicyFFDDIHybrid
 * 
 * The VM Placement policy runs as needed, while the VM Consolidation policy 
 * runs periodically. The VM Relocation policy is triggered with every Host 
 * Status Update: a Stress Check is performed on the Host and if the check is 
 * positive, the VM Relocation process is started.
 * 
 * @author Gaston Keller
 *
 */
public class CentralizedExperiment extends SimulationTask {

	private static Logger logger = Logger.getLogger(CentralizedExperiment.class);
	
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
	
	private double lower;				// Lower utilization threshold.
	private double target;				// Target utilization threshold.
	private double upper;				// Upper utilization threshold.
	private ServiceType serviceType;	// Type of service to generate.
	private boolean legacyLoadGen;		// Use legacy load generator.
	
	private enum ServiceType {STATIC, DYNAMIC, RANDOM;}
	
	public static void main(String args[]) {
		
		Simulation.initializeLogging();
		
		PrintStream printStream;
		try {
			printStream = new PrintStream("out_centralized");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		
		// Exp 1A
		runSimulationSet(printStream, SimTime.days(10), SimTime.days(2), 0.60, 0.85, 0.90, ServiceType.STATIC, true);
		
		// Exp 1B
//		runSimulationSet(printStream, SimTime.days(12), SimTime.days(4), 0.60, 0.85, 0.90, ServiceType.STATIC, true);
		
		// Exp 1C
//		runSimulationSet(printStream, SimTime.days(14), SimTime.days(6), 0.60, 0.85, 0.90, ServiceType.STATIC, true);
		
		// Exp 1D
//		runSimulationSet(printStream, SimTime.days(15), SimTime.days(7), 0.60, 0.85, 0.90, ServiceType.STATIC, true);
		
		// Exp 2A
//		runSimulationSet(printStream, SimTime.days(12), SimTime.days(4), 0.60, 0.85, 0.90, ServiceType.DYNAMIC, true);
		
		// Exp 3A
//		runSimulationSet(printStream, SimTime.days(12), SimTime.days(4), 0.60, 0.85, 0.90, ServiceType.RANDOM, true);
		
		printStream.println("Done");
		printStream.close();
	}
	
	private static void runSimulationSet(PrintStream out, 
			long duration,
			long metricRecordStart, 
			double lower,
			double target,
			double upper,
			ServiceType serviceType,
			boolean legacyLoadGen) {
		
		logger.info("Started New Simulation Set");
		logger.info(lower + "," + target + "," + upper + "," + duration + "," + metricRecordStart + "," + serviceType + "," + legacyLoadGen);
		
		List<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
		for (int i = 0; i < N_SEEDS; ++i)  {
			CentralizedExperiment e = new CentralizedExperiment("centralized-" + (i + 1), duration, metricRecordStart, randomSeeds[i]);
			e.setParameters(lower, target, upper, serviceType, legacyLoadGen);
			executor.addTask(e);
		}
		
		completedTasks = executor.execute(4);
		
		//output CSV
		out.println("Centralized Experiment");
		out.println("lower=" + lower + " | target=" + target + " | upper=" + upper +
				" | duration=" + duration + " | metricRecordStart=" + metricRecordStart +
				" | serviceType=" + serviceType + " | legacyLoadGen=" + legacyLoadGen);
		
		for(SimulationTask task : completedTasks) {
			if (completedTasks.indexOf(task) == 0) {
				task.getMetrics().printCSV(out);
			} else {
				task.getMetrics().printCSV(out, false);
			}
			
			// Conventional print.
			logger.info(task.getName());
			task.getMetrics().printDefault(logger);
		}
		out.println("");
		out.println("");
		
		out.flush();
	}
	
	public CentralizedExperiment(String name, long duration, long metricRecordStart) {
		super(name, duration);
		this.setMetricRecordStart(metricRecordStart);
	}
	
	public CentralizedExperiment(String name, long duration, long metricRecordStart, long randomSeed) {
		super(name, duration);
		this.setMetricRecordStart(metricRecordStart);
		this.setRandomSeed(randomSeed);
	}
	
	private void setParameters(double lower, double target, double upper, ServiceType serviceType, boolean legacyLoadGen) {
		this.lower = lower;
		this.target = target;
		this.upper = upper;
		this.serviceType = serviceType;
		this.legacyLoadGen = legacyLoadGen;
	}
	
	/**
	 * Configures the simulation, creating the data centre and its management infrastructure, 
	 * setting parameters, and configuring the Services Producer.
	 */
	@Override
	public void setup(Simulation simulation) {
		
		// Create data centre.
		DataCentre dc = ManFI2014TestEnvironment.createInfrastructure(simulation);
		
		// Create management infrastructure.
		AutonomicManager dcManager = this.createMgmtInfrastructure(simulation, dc);
		
		// Create and start the Services Producer.
		switch (serviceType) {
			case STATIC:	ManFI2014TestEnvironment.configureStaticServices(simulation, dcManager, legacyLoadGen);
							break;
			case DYNAMIC:	ManFI2014TestEnvironment.configureDynamicServices(simulation, dcManager, legacyLoadGen);
							break;
			case RANDOM:	ManFI2014TestEnvironment.configureRandomServices(simulation, dcManager, 1, 600, 1600, legacyLoadGen);
							break;
			default:		break;
	}
	}
	
	/**
	 * Creates the management infrastructure for the data centre, which includes creating 
	 * autonomic managers and setting their capabilities and policies.
	 */
	public AutonomicManager createMgmtInfrastructure(Simulation simulation, DataCentre dc) {
		
		// Create DC Manager.
		HostPoolManager hostPool = new HostPoolManager();
		AutonomicManager dcManager = new AutonomicManager(simulation, hostPool);
		
		// Install management policies in the autonomic manager.
		dcManager.installPolicy(new ReactiveHostStatusPolicy(5));
		dcManager.installPolicy(new VmPlacementPolicyFFMHybrid(lower, upper, target));
		dcManager.installPolicy(new VmRelocationPolicyHybridReactive(lower, upper, target));
		dcManager.installPolicy(new VmConsolidationPolicyFFDDIHybrid(lower, upper, target), SimTime.hours(1), SimTime.hours(1));
		
		// TODO: Autonomic manager is NOT installed anywhere.
		
		for (Host host : dc.getHosts()) {
			
			// Create Host's autonomic manager.
			AutonomicManager hostManager = new AutonomicManager(simulation, new HostManager(host));
			
			// Install management policies in the autonomic manager.
			hostManager.installPolicy(new HostMonitoringPolicy(dcManager), SimTime.minutes(2), SimTime.minutes(simulation.getRandom().nextInt(5)));
			hostManager.installPolicy(new HostOperationsPolicy());
			
			// Install autonomic manager in the Host.
			host.installAutonomicManager(hostManager);
			
			// Add Host and its autonomic manager to the capability of the hosting Rack.
			hostPool.addHost(host, hostManager);
			
		}
		
		return dcManager;
	}

}
