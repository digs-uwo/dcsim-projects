package edu.uwo.csd.dcsim.extras.experiments;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.core.DaemonScheduler;
import edu.uwo.csd.dcsim.core.FixedIntervalDaemonScheduler;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.extras.policies.*;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.vm.VMAllocationRequest;
import edu.uwo.csd.dcsim.core.metrics.*;

public class StrategySwitching extends DCSimulationTask {
	
	private static final double POW_COST = 0.08;
	private static final double SLA_COST = 12741.7561;
	
	public static double powerNormal, powerHigh, slaNormal, slaHigh;
	
	private static SimulationExecutor executor;

	public static void main(String args[]) throws IOException{
		
		Simulation.initializeLogging();
		
		executor = new SimulationExecutor();
		
/*		
		//Run single simulation:		

		StrategySwitching task = new StrategySwitching("strat-switching", 6198910678692541341l);
//		StrategySwitching task = new StrategySwitching("strat-switching", 5646441053220106016l);
//		StrategySwitching task = new StrategySwitching("strat-switching", -5705302823151233610l);
//		StrategySwitching task = new StrategySwitching("strat-switching", 8289672009575825404l);
//		StrategySwitching task = new StrategySwitching("strat-switching", -4637549055860880177l);
		
		task.run();
		
		DataCentreTestEnvironment.printMetrics(task.getResults());
*/	
/*
		//brute force switching threshold search
		
		File outputFile = new File("results.csv");
		//FileWriter out = new FileWriter(outputFile, true);
		FileWriter out = new FileWriter(outputFile);
		out.write("Results of simulations\n");
		out.close();
		out = new FileWriter(outputFile, true);

		double slaMax = 0.019;
		double slaMin = 0.005;
		double powMax = 1.43;
		double powMin = 1.12;
		
		double slaStep = (slaMax - slaMin) / 7;
		double powStep = (powMax - powMin) / 15;
		
		for(double sla_norm = slaMin; sla_norm <= slaMax; sla_norm += slaStep){
			for(double sla_high = sla_norm; sla_high <= slaMax; sla_high += slaStep){
				for(double pow_norm = powMin; pow_norm <= powMax; pow_norm += powStep){
					for(double pow_high = pow_norm; pow_high <= powMax; pow_high += powStep){
						//run task with given parameters
						out.write(run(sla_high, sla_norm, pow_high, pow_norm) + "\n");
						out.flush();
					}
				}
			}
		}
		out.close();
		*/
		File outputFile = new File("results.csv");
		//FileWriter out = new FileWriter(outputFile, true);
		FileWriter out = new FileWriter(outputFile);
		out.write("Results of simulations\n");
		out.close();
		out = new FileWriter(outputFile, true);
		
		double slaStep = 0.001;
		double start = System.currentTimeMillis();
		for(double sla = 0.005; sla<=0.005; sla += slaStep){
			executor.addTask(new StrategySwitching("strat-switching", 6198910678692541341l, sla, sla, 1.3886666667, 1.2646666667));	
		}
		
		Collection<SimulationTask> completedTasks = executor.execute();
		
		for(SimulationTask task : completedTasks){
			StrategySwitching thisTask = (StrategySwitching) task;
			
			DataCentreTestEnvironment.printMetrics(task.getResults());
			
			double powCost=0, slaCost=0, totalCost=0;
			
			for(Metric metric : task.getResults()){
				if(metric.getName().equals("powerConsumed")){
					powCost = (metric.getValue()/3600000) * POW_COST;
				}else if(metric.getName().equals("slaViolation")){
					slaCost = metric.getValue() * SLA_COST;
				}
			}
			totalCost = powCost + slaCost;
			
			out.write(thisTask.slaHigh + ", " + thisTask.slaNormal + ", " + thisTask.powerHigh + ", " + thisTask.powerNormal + ", " + powCost + ", " + slaCost + ", " + totalCost + "\n");
		}
		
		out.close();
		//System.out.println(run(0.006, 0.006, 1.43, 1.43));
		
	}
	
	public StrategySwitching(String name, long randomSeed) {
		super(name, 864000000);
		this.setMetricRecordStart(86400000);
		this.setRandomSeed(randomSeed);
	}
	
	//constructor used when switching thresholds are used
	public StrategySwitching(String name, long randomSeed, double slaHigh, double slaNormal, double powerHigh, double powerNormal){
		super(name, 864000000);
		this.setMetricRecordStart(86400000);
		this.setRandomSeed(randomSeed);
		this.slaHigh = slaHigh;
		this.slaNormal = slaNormal;
		this.powerHigh = powerHigh;
		this.powerNormal = powerNormal;
	}

	
	public StrategySwitching(String name) {
		super(name, 864000000);
		this.setMetricRecordStart(0);
	}
	
	//run a single task with supplied switching thresholds, return string representing thresholds and total calculated cost for write to file
	private static String run(double slaHigh, double slaNormal, double powerHigh, double powerNormal){
		StrategySwitching task = new StrategySwitching("strat-switching", 6198910678692541341l, slaHigh, slaNormal, powerHigh, powerNormal);
		task.run();
		
		DataCentreTestEnvironment.printMetrics(task.getResults());
		
		double powCost=0, slaCost=0, totalCost=0;
		
		for(Metric metric : task.getResults()){
			if(metric.getName().equals("powerConsumed")){
				powCost = (metric.getValue()/3600000) * POW_COST;
			}else if(metric.getName().equals("slaViolation")){
				slaCost = metric.getValue() * SLA_COST;
			}
		}
		totalCost = powCost + slaCost;
		return (slaHigh + ", " + slaNormal + ", " + powerHigh + ", " + powerNormal + ", " + powCost + ", " + slaCost + ", " + totalCost);
	}
	
	@Override
	public void setup(DataCentreSimulation simulation) {
		
		DataCentre dc = DataCentreTestEnvironment.createDataCentre(simulation);
		simulation.addDatacentre(dc);
		ArrayList<VMAllocationRequest> vmList = DataCentreTestEnvironment.createVmList(simulation, true);
		DataCentreTestEnvironment.placeVms(vmList, dc);
		
		DCUtilizationMonitor dcMon = new DCUtilizationMonitor(simulation, 120000, 10, dc);
		simulation.addMonitor(dcMon);
		
		
		//relocation policies
//		VMAllocationPolicyGreedy powerPolicy = new VMAllocationPolicyGreedy(dc, 0.6, 0.90, 0.90);
//		VMAllocationPolicyGreedy slaPolicy = new VMAllocationPolicyGreedy(dc, 0.4, 0.75, 0.75);
//		VMAllocationPolicyGreedy balanced = new VMAllocationPolicyGreedy(dc, 0.5, 0.85, 0.85);
		
		VMRelocationPolicyFFDI slaPolicy = new VMRelocationPolicyFFDI(dc, dcMon, 0.65, 0.85, 0.85);
		VMRelocationPolicyFFID powerPolicy = new VMRelocationPolicyFFID(dc, dcMon, 0.65, 0.85, 0.85);
		
		/*
		SlaVsPowerSwitchingPolicy switchingPolicy = new SlaVsPowerSwitchingPolicy.Builder(dcMon)
				.slaPolicy(slaPolicy)
				.powerPolicy(powerPolicy)
				.switchingInterval(3600000)
				.slaHigh(0.008)
				.slaNormal(0.003)
				.powerHigh(1.33)
				.powerNormal(1.28)
				.optimalPowerPerCpu(0.01165)
				.build();
		*/
		SlaVsPowerSwitchingPolicy switchingPolicy = new SlaVsPowerSwitchingPolicy.Builder(dcMon)
		.slaPolicy(slaPolicy)
		.powerPolicy(powerPolicy)
		.switchingInterval(3600000)
		.slaHigh(slaHigh)
		.slaNormal(slaNormal)
		.powerHigh(powerHigh)
		.powerNormal(powerNormal)
		.optimalPowerPerCpu(0.01165)
		.build();
		
		DaemonScheduler policyDaemon = new FixedIntervalDaemonScheduler(simulation, 600000, switchingPolicy);
		policyDaemon.start(600000);
		
		
		//consolidation policy
		VMConsolidationPolicySimple consolidationPolicy = new VMConsolidationPolicySimple(dc, dcMon, 0.65, 0.9);
		DaemonScheduler consolidationDaemon = new FixedIntervalDaemonScheduler(simulation, 14400000, consolidationPolicy);
		consolidationDaemon.start(14401000);
		
		
	}

}
