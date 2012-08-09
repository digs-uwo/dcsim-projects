package edu.uwo.csd.dcsim.extras.experiments;

import java.util.Collection;
import java.io.*;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.DCSimulationTask;
import edu.uwo.csd.dcsim.DCSimulationTraceWriter;
import edu.uwo.csd.dcsim.SimulationExecutor;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.metrics.Metric;

/**
 * This experiment generates baseline measurements from GreenStrategy.java and SLAFriendlyStrategy.java
 * and uses these as the basis of evaluating the performance of executions of FullStrategySwitching.java
 * 
 * @author Graham Foster
 *
 */

public class IM2012StratSwitching {

	private static Logger logger = Logger.getLogger(FullStrategySwitching.class);
	private static double powerEffMin, powerEffMax, slaMin, slaMax;
	private static SimulationExecutor<DCSimulationTask> executor;
	private static int numTasksInQueue=0;
	private static File outputFile = null;
	private static FileWriter out;
	
	public static void main(String args[]) {
		try{
		outputFile = new File("results.csv");
		out = new FileWriter(outputFile);
		}catch(IOException e){}
		
		Simulation.initializeLogging();
		
		/*
		 * run a lot of experiments searching for sla/power thresholds
		 */
		//runSlaPowerThresholdSearch(6198910678692541341l);
		
		/*
		 * run a lot of experiments searching for datacenter utilization switching thresholds
		 */
		//runUtilizationThresholdSearch(6198910678692541341l);
		
		/*
		 * run single experiments
		 */
		runSingleTrial(6198910678692541341l, 1, 0.011, 0.001);
		runSingleTrial(5646441053220106016l, 2, 0.011, 0.001);
		runSingleTrial(-5705302823151233610l, 3, 0.011, 0.001);
		runSingleTrial(8289672009575825404l, 4, 0.011, 0.001);
		runSingleTrial(-4637549055860880177l, 5, 0.011, 0.001);
	}
	
	/**
	 * Generate baseline measurements for green strategy and sla strategy
	 * @param seed Random seed used during simulation
	 */
	private static void generateBaseline(long seed){

		Collection<DCSimulationTask> completedTasks;
		executor = new SimulationExecutor<DCSimulationTask>();
		
		executor.addTask(new GreenStrategy("green-baseline", seed));
		executor.addTask(new SLAFriendlyStrategy("sla-baseline", seed));
		
		completedTasks = executor.execute();
		for(DCSimulationTask task : completedTasks){
			if(task.getName().equals("green-baseline")){
				slaMax = extractSLA(task.getResults());
				powerEffMax = extractPowerEff(task.getResults());
			}
			else if(task.getName().equals("sla-baseline")){
				slaMin = extractSLA(task.getResults());
				powerEffMin = extractPowerEff(task.getResults());
			}
			IM2012TestEnvironment.printMetrics(task.getResults());
		}
		
		logger.info("Baseline Measurements are:");
		logger.info("slaMin: " + slaMin + ", slaMax: " + slaMax);
		logger.info("powerEffMin: " + powerEffMin + ", powerEffMax: " + powerEffMax);
	}
	
	/**
	 * Extract power efficiency measurement from collection of metrics
	 * @param metrics
	 * @return power measured in kW/h
	 */
	private static double extractPowerEff(Collection<Metric> metrics){
		for(Metric metric : metrics){
			if(metric.getName().equals("powerEfficiency")){
				return metric.getValue();
			}
		}
		throw new RuntimeException("Could not extract power from metric collection");
	}
	
	/**
	 * Extract power measurement from collection of metrics
	 * @param metrics
	 * @return power measured in kW/h
	 */
	private static double extractPower(Collection<Metric> metrics){
		for(Metric metric : metrics){
			if(metric.getName().equals("powerConsumption")){
				//divide by 3600000 to convert from watt/seconds to kW/h
				return metric.getValue()/3600000;
			}
		}
		throw new RuntimeException("Could not extract power from metric collection");
	}
	
	/**
	 * Extract SLA measurement from collection of metrics
	 * @param metrics
	 * @return sla (0% - 100%)
	 */
	private static double extractSLA(Collection<Metric> metrics){
		for(Metric metric : metrics){
			if(metric.getName().equals("slaViolation")){
				return metric.getValue()*100;
			}
		}
		throw new RuntimeException("Could not extract sla from metric collection");
	}
	
	/**
	 * 
	 * @param power Power Measurement from simulation
	 * @param sla SLA Measurement from simulation
	 * @return simulation score determined as the euclidean distance between (0,0) and power and sla measurements normalized against baselines
	 */
	private static double getScore(double powerEff, double sla){
		double normPowerEff = (powerEffMax - powerEff) / (powerEffMax - powerEffMin);
		double normSla = (sla - slaMin) / (slaMax - slaMin);
		return Math.sqrt((normPowerEff * normPowerEff) + (normSla * normSla));
	}
	
	private static void runSingleTrial(long randomSeed, int trialNum, double toPowerThreshold, double toSlaThreshold){
		generateBaseline(randomSeed);
		runOnce(new UtilStrategySwitching("strat-switching-" + trialNum, randomSeed, toPowerThreshold, toSlaThreshold));
		runOnce(new BalancedStrategy("balanced-" + trialNum, randomSeed));
	}
	
	private static void runOnce(DCSimulationTask task){
		
		task.run();
		
		double powerEff = extractPowerEff(task.getResults());
		double sla = extractSLA(task.getResults());
		double score = getScore(powerEff,sla);
		
		IM2012TestEnvironment.printMetrics(task.getResults());
		
		logger.info(task.getName() + " scored: " + score);
		
		DCSimulationTraceWriter traceWriter = new DCSimulationTraceWriter(task);
		traceWriter.writeTrace();
	}
	
	private static void runSlaPowerThresholdSearch(long randomSeed){
		//generate power and sla-focused baseline measurements 
		generateBaseline(randomSeed);
		
		int numSlaSteps = 8;
		int numPowerSteps = 8;
		
		double powerStart = 1.2612604587;
		double powerEnd = 1.5484970697;
		double slaStart = 0.05409426290411103;
		double slaEnd = 0.540772608217123;
		
		//determine interval between attempts.  Divides by numPowerSteps-1 to include the top and bottom threshold in the search
		double powerStep = (powerEnd - powerStart) / (numPowerSteps-1);
		double slaStep = (slaEnd - slaStart) / (numSlaSteps-1);

		executor = new SimulationExecutor<DCSimulationTask>();
		
		//create tasks
		for(double powerNorm = powerStart; powerNorm <= powerEnd; powerNorm += powerStep){
			for(double powerHigh = powerNorm; powerHigh <= powerEnd; powerHigh += powerStep){
				for(double slaNorm = slaStart; slaNorm <= slaEnd; slaNorm += slaStep){
					for(double slaHigh = slaNorm; slaHigh <= slaEnd; slaHigh += slaStep){
						
						runTask(new FullStrategySwitching("StratSwitching,"+slaHigh+","+slaNorm+","+powerHigh+","+powerNorm+",", randomSeed, slaHigh, slaNorm, powerHigh, powerNorm));
						
					}
				}
			}
		}

		Collection<DCSimulationTask> completedTasks = executor.execute();
		
		for(DCSimulationTask task : completedTasks){
			double power = extractPowerEff(task.getResults());
			double sla = extractSLA(task.getResults());
			double score = getScore(power,sla);
			
			IM2012TestEnvironment.printMetrics(task.getResults());
			
			addToFile(task.getName() + " scored: " + score + "\n");
			logger.info(task.getName() + " scored: " + score);
		}
		
		executor = new SimulationExecutor<DCSimulationTask>();
	}
	
	private static void runUtilizationThresholdSearch(long randomSeed){
		//generate power and sla-focused baseline measurements 
		generateBaseline(randomSeed);
		
		int numSteps = 62;
		double start = -0.015;
		double end = 0.015;
		
		//divide by steps-1 to include the end value as well
		double step = (end - start) / (numSteps-1);

		executor = new SimulationExecutor<DCSimulationTask>();
		
		//create tasks
		for(double toPower = start; toPower <= end; toPower += step){
			for(double toSla = start; toSla <= end; toSla += step){
				runTask(new UtilStrategySwitching("StratSwitching,"+toPower+","+toSla+",",randomSeed,toPower,toSla));
			}
		}
		
		Collection<DCSimulationTask> completedTasks = executor.execute();
		
		for(DCSimulationTask task : completedTasks){
			double power = extractPowerEff(task.getResults());
			double sla = extractSLA(task.getResults());
			double score = getScore(power,sla);
			
			IM2012TestEnvironment.printMetrics(task.getResults());
			
			addToFile(task.getName() + " scored: " + score + "\n");
			logger.info(task.getName() + " scored: " + score);
		}
		
		executor = new SimulationExecutor<DCSimulationTask>();
	}
	
	private static void runTask(DCSimulationTask newTask){
		executor.addTask(newTask);
		numTasksInQueue++;
		
		if(numTasksInQueue == 4){
			Collection<DCSimulationTask> completedTasks = executor.execute();
			
			for(DCSimulationTask task : completedTasks){
				double power = extractPowerEff(task.getResults());
				double sla = extractSLA(task.getResults());
				double score = getScore(power,sla);
				
				IM2012TestEnvironment.printMetrics(task.getResults());
				
				addToFile(task.getName() + " scored: " + score + "\n");
				logger.info(task.getName() + " scored: " + score);
			}
			
			executor = new SimulationExecutor<DCSimulationTask>();
			numTasksInQueue = 0;
		}
	}
	
	private static void addToFile(String message){
		try{
		out.write(message);
		out.flush();
		}catch(IOException e){}
	}
	
}
