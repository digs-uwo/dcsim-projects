package edu.uwo.csd.dcsim.extras.experiments;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.DCSimulationTask;
import edu.uwo.csd.dcsim.SimulationExecutor;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.metrics.Metric;

/**
 * Performs different Brute Force Searches for different simulation parameters 
 * or thresholds.
 * 
 * @author Gaston Keller
 *
 */
public class BruteForceSearch {

	private static Logger logger = Logger.getLogger(BruteForceSearch.class);
	private static double powerEffMin, powerEffMax, slaMin, slaMax;
	private static SimulationExecutor<DCSimulationTask> executor;
	private static int numTasksInQueue = 0;
	private static int maxTasksInQueue = 4;
	private static File outputFile = null;
	private static FileWriter out;
	public static final long[] SEEDS = {6198910678692541341l, 
										5646441053220106016l, 
										-5705302823151233610l, 
										8289672009575825404l, 
										-4637549055860880177l};
	
	/**
	 * Create a new BruteForceSearch.
	 */
	public static void main(String[] args) {
		try {
			outputFile = new File("results.csv");
			out = new FileWriter(outputFile);
		} catch (IOException e){}
		
		Simulation.initializeLogging();
		
		// Run brute force search to find parameters for Distance to Goals approach.
		double minWorstSlaGoal = 0.001;
		double maxWorstSlaGoal = 0.020;
		double stepWorstSlaGoal = 0.001;
		double minWorstPowerEffCo = 0.70;
		double maxWorstPowerEffCo = 0.90;
		double stepWorstPowerEffCo = 0.01;
		for (long seed : SEEDS)
			runDistanceParametersSearch(seed, minWorstSlaGoal, maxWorstSlaGoal, stepWorstSlaGoal, minWorstPowerEffCo, maxWorstPowerEffCo, stepWorstPowerEffCo);
		
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
//		runSingleTrial(6198910678692541341l, 1, 0.011, 0.001);
//		runSingleTrial(5646441053220106016l, 2, 0.011, 0.001);
//		runSingleTrial(-5705302823151233610l, 3, 0.011, 0.001);
//		runSingleTrial(8289672009575825404l, 4, 0.011, 0.001);
//		runSingleTrial(-4637549055860880177l, 5, 0.011, 0.001);
	}
	
	private static void addToFile(String message){
		try{
		out.write(message);
		out.flush();
		}catch(IOException e){}
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
	 * Generate baseline measurements for Power and SLA-friendly strategies.
	 * 
	 * @param seed Random seed used during simulation.
	 */
	private static void generateBaseline(long seed){
		Collection<DCSimulationTask> completedTasks;
		executor = new SimulationExecutor<DCSimulationTask>();
		executor.addTask(new GreenStrategy("power-baseline", seed));
		executor.addTask(new SLAFriendlyStrategy("sla-baseline", seed));
		completedTasks = executor.execute();
		
		for(DCSimulationTask task : completedTasks){
			if (task.getName().equals("power-baseline")) {
				slaMax = extractSLA(task.getResults());
				powerEffMax = extractPowerEff(task.getResults());
			}
			else if (task.getName().equals("sla-baseline")) {
				slaMin = extractSLA(task.getResults());
				powerEffMin = extractPowerEff(task.getResults());
			}
			//IM2012TestEnvironment.printMetrics(task.getResults());
		}
		
		logger.info("Baseline Measurements are:");
		logger.info("slaMin: " + slaMin + ", slaMax: " + slaMax);
		logger.info("powerEffMin: " + powerEffMin + ", powerEffMax: " + powerEffMax);
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
	
	private static void recordSimResults(Collection<DCSimulationTask> completedTasks) {
		for(DCSimulationTask task : completedTasks){
			double power = Utility.roundDouble(extractPowerEff(task.getResults()),3);
			double sla = Utility.roundDouble(extractSLA(task.getResults()),3);
			double score = Utility.roundDouble(getScore(power,sla),3);
			long seed = task.getRandomSeed();
			
			//IM2012TestEnvironment.printMetrics(task.getResults());
			
			addToFile(task.getName() + sla + "," + power + "," + score + "," + seed + "\n");
			logger.info(task.getName() + " sla: " + sla + ", power: " + power + ", scored: " + score + ", seed: " + seed);
		}
	}
	
	/**
	 * Runs the Balanced strategy for the given random seed and logs the 
	 * results.
	 */
	private static void runBalanced(long randomSeed) {
		DCSimulationTask task = new BalancedStrategy("balanced,", randomSeed);
		task.run();
		Collection<DCSimulationTask> completedTasks = new ArrayList<DCSimulationTask>();
		completedTasks.add(task);
		recordSimResults(completedTasks);
	}
	
	private static void runDistanceParametersSearch(long randomSeed, 
													double minWorstSlaGoal, 
													double maxWorstSlaGoal, 
													double stepWorstSlaGoal, 
													double minWorstPowerEffCo, 
													double maxWorstPowerEffCo, 
													double stepWorstPowerEffCo){
		
		// Generate Power and SLA-friendly baseline measurements. 
		generateBaseline(randomSeed);
		
		// Run the Balanced strategy.
		runBalanced(randomSeed);
		
		executor = new SimulationExecutor<DCSimulationTask>();
		
		// Create simulation tasks.
		for (double sla = minWorstSlaGoal; sla <= maxWorstSlaGoal; sla = Utility.roundDouble(sla + stepWorstSlaGoal)) {
			for (double power = minWorstPowerEffCo; power <= maxWorstPowerEffCo; power = Utility.roundDouble(power + stepWorstPowerEffCo)) {
				// Add task to queue.
				runTask(new DistanceToGoalStrategySwitching("goal-strat-switching," + sla + "," + power + ",", randomSeed , sla, power));
			}
		}
		
		Collection<DCSimulationTask> completedTasks = executor.execute();
		recordSimResults(completedTasks);
	}
	
	/**
	 * Executes simulation tasks in groups of _maxTasksInQueue_ , so as not to 
	 * run out of memory.
	 */
	private static void runTask(DCSimulationTask newTask){
		// Add simulation task to queue.
		executor.addTask(newTask);
		numTasksInQueue++;
		
		// Execute tasks in queue if we have reached queue's max capacity.
		if (numTasksInQueue == maxTasksInQueue){
			Collection<DCSimulationTask> completedTasks = executor.execute();
			recordSimResults(completedTasks);
			executor = new SimulationExecutor<DCSimulationTask>();
			numTasksInQueue = 0;
		}
	}

}
