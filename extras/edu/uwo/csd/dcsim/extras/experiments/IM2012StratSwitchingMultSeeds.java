package edu.uwo.csd.dcsim.extras.experiments;

import java.util.ArrayList;
import java.util.Random;
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

public class IM2012StratSwitchingMultSeeds {

	private static Logger logger = Logger.getLogger(SlaPowerStrategySwitching.class);
	private static final int NUM_SEEDS = 100;
	private static int executorSize = 0;
	private static double[] powerEffMin, powerEffMax, slaMin, slaMax;
	private static long[] seeds;
	private static double avgBalancedScore;
	private static SimulationExecutor<DCSimulationTask> executor;
	private static int numTasksInQueue=0;
	private static File outputFile = null, scoreDetailsFile = null, baselineFile = null;
	private static FileWriter out, scoreOut, baselineOut;
	
	public static void main(String args[]) {
		try{
		outputFile = new File("results.csv");
		out = new FileWriter(outputFile);
		scoreDetailsFile = new File("scoreDetails.csv");
		scoreOut = new FileWriter(scoreDetailsFile);
		baselineFile = new File("baselines");
		baselineOut = new FileWriter(baselineFile);
		}catch(IOException e){}
		
		Simulation.initializeLogging();

		seeds = new long[NUM_SEEDS];
		powerEffMin = new double[NUM_SEEDS];
		powerEffMax = new double[NUM_SEEDS];
		slaMin = new double[NUM_SEEDS];
		slaMax = new double[NUM_SEEDS];
		
		Random random = new Random(6198910678692541341l);
		for(int i=0; i<seeds.length; i++){
			seeds[i] = random.nextLong();
			System.out.println(seeds[i]);
		}
		
//		seeds[0] = 6198910678692541341l;
//		seeds[1] = 5646441053220106016l;
//		seeds[2] = -5705302823151233610l;
//		seeds[3] = 8289672009575825404l;
//		seeds[4] = -4637549055860880177l;
		
		generateAllBaselines();
//		powerEffMin[0] = 61.92241095656373;
//		powerEffMax[0] = 77.09985473605255;
//		slaMin[0] = 0.03311026637789721;
//		slaMax[0] = 0.4009955340725556;
//		powerEffMin[1] = 59.485866913721445;
//		powerEffMax[1] = 74.17411507094413;
//		slaMin[1] = 0.03650931421540349;
//		slaMax[1] = 0.4987131358753138;
//		powerEffMin[2] = 62.95552844899733;
//		powerEffMax[2] = 77.5460522005403;
//		slaMin[2] = 0.027634056743937423;
//		slaMax[2] = 0.4602775962850923;
//		powerEffMin[3] = 64.17834144238701;
//		powerEffMax[3] = 77.48616131976436;
//		slaMin[3] = 0.021918829072261666;
//		slaMax[3] = 0.39742745551468467;
//		powerEffMin[4] = 62.040434403958976;
//		powerEffMax[4] = 77.59461278260527;
//		slaMin[4] = 0.029579017843471143;
//		slaMax[4] = 0.3946083214076906;
		
		
		for(int i=0; i<seeds.length; i++){
			System.out.println("Baselines: " + powerEffMin[i] + ", " + powerEffMax[i] + ", " + slaMin[i] + ", " + slaMax[i]);
			try{
			baselineOut.append(seeds[i]+","+slaMin[i]+","+slaMax[i]+","+powerEffMin[i]+","+powerEffMax[i]+"\n");
			}catch(IOException e){}
		}
		
//		avgBalancedScore = 0.6779355385647505;
		avgBalancedScore = getAvgBalancedScore();
		
		System.out.println("Average balanced score: " + avgBalancedScore);
		
		/*
		 * run a lot of experiments searching for datacenter utilization switching thresholds
		 */
		//runUtilizationThresholdSearch();
		
		/*
		 * run single experiments
		 */
		runSingleTrial(0.0025517241, 0.0025517241);
	}
	
	/**
	 * Generate and store baseline values for each seed
	 */
	private static void generateAllBaselines(){
		for(int i=0; i<seeds.length; i++){
			logger.info("Generating baselines for seed number: " + (i+1) + " of " + seeds.length);
			generateBaseline(i, seeds[i]);
		}
	}
	
	/**
	 * Generate baseline measurements for green strategy and sla strategy
	 * @param seed Random seed used during simulation
	 */
	private static void generateBaseline(int seedNum, long seed){

		Collection<DCSimulationTask> completedTasks;
		executor = new SimulationExecutor<DCSimulationTask>();
		
		executor.addTask(new GreenStrategy("green-baseline", seed));
		executor.addTask(new SLAFriendlyStrategy("sla-baseline", seed));
		
		completedTasks = executor.execute();
		for(DCSimulationTask task : completedTasks){
			if(task.getName().equals("green-baseline")){
				slaMax[seedNum] = extractSLA(task.getResults());
				powerEffMax[seedNum] = extractPowerEff(task.getResults());
			}
			else if(task.getName().equals("sla-baseline")){
				slaMin[seedNum] = extractSLA(task.getResults());
				powerEffMin[seedNum] = extractPowerEff(task.getResults());
			}
			//IM2012TestEnvironment.printMetrics(task.getResults());
		}
		
		logger.info("Baseline Measurements are:");
		logger.info("slaMin[" + seedNum + "]: " + slaMin[seedNum] + ", slaMax: " + slaMax[seedNum]);
		logger.info("powerEffMin[" + seedNum + "]: " + powerEffMin[seedNum] + ", powerEffMax: " + powerEffMax[seedNum]);
	}
	
	private static double getAvgBalancedScore(){
		double[] balancedResults = new double[seeds.length];
		for(int i=0; i<seeds.length; i++){
			balancedResults[i] = runSingleBalanced(seeds[i], i); 
		}
		
		double res = 0;
		for(double item : balancedResults){
			res += item;
		}
		return res /= seeds.length;
	}
	
	private static double runSingleBalanced(long randomSeed, int seedNum){
		DCSimulationTask task = new BalancedStrategy("balanced-" + randomSeed, randomSeed);
		task.run();
		
		double powerEff = extractPowerEff(task.getResults());
		double sla = extractSLA(task.getResults());
		double score = getScore(powerEff,sla,seedNum, true);
		
		logger.info(task.getName() + " scored: " + score);
		return score;
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
	private static double getScore(double powerEff, double sla, int i, boolean isBalanced){
		double normPowerEff = (powerEffMax[i] - powerEff) / (powerEffMax[i] - powerEffMin[i]);
		double normSla = (sla - slaMin[i]) / (slaMax[i] - slaMin[i]);
		double score = Math.sqrt((normPowerEff * normPowerEff) + (normSla * normSla));
		
		try{
			if(isBalanced)
				scoreOut.append("balanced,"+seeds[i]+","+normPowerEff+","+normSla+","+score);
			else
				scoreOut.append("stratSwitch,"+seeds[i]+","+normPowerEff+","+normSla+","+score);
		}catch(IOException e){}
		
		return score;
	}
	
	/**
	 * Run a single candidate task.  First execute the baseline tasks, followed by the candidate task and the balanced task.
	 * Calculates score for candidate and balanced tasks and writes each to trace files
	 * @param randomSeed
	 * @param trialNum This number will be appended to the task names and trace file names
	 * @param toPowerThreshold
	 * @param toSlaThreshold
	 */
	private static void runSingleTrial(double toPowerThreshold, double toSlaThreshold){
		ArrayList<DCSimulationTask> tasks = new ArrayList<DCSimulationTask>();
		executor = new SimulationExecutor<DCSimulationTask>();
		for(int i=0; i<seeds.length; i++){
			tasks.add(new UtilStrategySwitching("strat-switching-"+(i+1),seeds[i],toPowerThreshold,toSlaThreshold));
			addTaskSafely(tasks.get(i));
		}
		executor.execute();
		
		double scoreSum = 0;
		for(int i=0; i<tasks.size(); i++){
			double power = extractPowerEff(tasks.get(i).getResults());
			double sla = extractSLA(tasks.get(i).getResults());
			double score = getScore(power,sla,i,false);
			
			scoreSum += score;
			
			IM2012TestEnvironment.printMetrics(tasks.get(i).getResults());
			
			logger.info(tasks.get(i).getName() + " scored: " + score);
			
			DCSimulationTraceWriter traceWriter = new DCSimulationTraceWriter(tasks.get(i));
			traceWriter.writeTrace();
		}
		scoreSum /= seeds.length;

		logger.info(tasks.get(0).getName() + " scored on average: " + scoreSum + "\n");
		logger.info("This is " + (((avgBalancedScore-scoreSum)/avgBalancedScore)*100) + "% better than the balanced policy's average performance");
	}
	
	private static void addTaskSafely(DCSimulationTask task){
		executorSize++;
		executor.addTask(task);
		if(executorSize == 4){
			executor.execute();
			executor = new SimulationExecutor<DCSimulationTask>();
			executorSize = 0;
		}
	}
	
	private static void runUtilizationThresholdSearch(){
		
		int numSteps = 30;
		double start = -0.008;
		double end = 0.01;
		
		//divide by steps-1 to include the end value as well
		double step = (end - start) / (numSteps-1);
		
		//create tasks
		for(double toPower = start; toPower <= end; toPower += step){
			for(double toSla = start; toSla <= end; toSla += step){
				executor = new SimulationExecutor<DCSimulationTask>();
				ArrayList<DCSimulationTask> tasks = new ArrayList<DCSimulationTask>();
				for(int i=0; i<seeds.length; i++){
					tasks.add(new UtilStrategySwitching("StratSwitching,"+toPower+","+toSla+",",seeds[i],toPower,toSla));
					executor.addTask(tasks.get(i));
				}
				executor.execute();
				
				double scoreSum = 0;
				for(int i=0; i<tasks.size(); i++){
					double power = extractPowerEff(tasks.get(i).getResults());
					double sla = extractSLA(tasks.get(i).getResults());
					double score = getScore(power,sla,i, false);
					
					scoreSum += score;
					
					//IM2012TestEnvironment.printMetrics(tasks.get(i).getResults());
					
					logger.info(tasks.get(i).getName() + " scored: " + score);
				}
				scoreSum /= seeds.length;
				
				logger.info("tasks named: " + tasks.get(0).getName() + " scored: " + (((avgBalancedScore-scoreSum)/avgBalancedScore)*100) + "% better than balanced.");

				addToFile(tasks.get(0).getName() + " scored: " + (((avgBalancedScore-scoreSum)/avgBalancedScore)*100) + "%\n");
			}
		}
	}
	
	private static void addToFile(String message){
		try{
		out.write(message);
		out.flush();
		}catch(IOException e){}
	}
	
}
