package edu.uwo.csd.dcsim.extras.experiments;

import java.util.Collection;

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
	private static double powerMin, powerMax, slaMin, slaMax;
	
	public static void main(String args[]) {
		
		Simulation.initializeLogging();
		
		//generate power and sla-focused baseline measurements
		generateBaseline(6198910678692541341l);
		
		Collection<DCSimulationTask> completedTasks;
		SimulationExecutor<DCSimulationTask> executor = new SimulationExecutor<DCSimulationTask>();
		
		executor.addTask(new FullStrategySwitching("strat-switching-1", 6198910678692541341l));
		
		completedTasks = executor.execute();
		for(DCSimulationTask task : completedTasks){
			double power = extractPower(task.getResults());
			double sla = extractSLA(task.getResults());
			double score = getScore(power,sla);
			
			IM2012TestEnvironment.printMetrics(task.getResults());
			
			logger.info(task.getName() + " scored: " + score);
			
			DCSimulationTraceWriter traceWriter = new DCSimulationTraceWriter(task);
			traceWriter.writeTrace();
		}
	}
	
	/**
	 * Generate baseline measurements for green strategy and sla strategy
	 * @param seed Random seed used during simulation
	 */
	private static void generateBaseline(long seed){
		
		double power, sla;

		Collection<DCSimulationTask> completedTasks;
		SimulationExecutor<DCSimulationTask> executor = new SimulationExecutor<DCSimulationTask>();
		
		executor.addTask(new GreenStrategy("green-baseline", seed));
		executor.addTask(new SLAFriendlyStrategy("sla-baseline", seed));
		
		completedTasks = executor.execute();
		for(DCSimulationTask task : completedTasks){
			if(task.getName().equals("green-baseline")){
				slaMax = extractSLA(task.getResults());
				powerMin = extractPower(task.getResults());
			}
			else if(task.getName().equals("sla-baseline")){
				slaMin = extractSLA(task.getResults());
				powerMax = extractPower(task.getResults());
			}
			IM2012TestEnvironment.printMetrics(task.getResults());
		}
		
		logger.info("Baseline Measurements are:");
		logger.info("slaMin: " + slaMin + ", slaMax: " + slaMax);
		logger.info("powerMin: " + powerMin + ", powerMax: " + powerMax);
	}
	
	/**
	 * Extract power measurement from collection of metrics
	 * @param metrics
	 * @return power measured in kW/h
	 */
	private static double extractPower(Collection<Metric> metrics){
		for(Metric metric : metrics){
			if(metric.getName().equals("powerConsumption")){
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
	private static double getScore(double power, double sla){
		double normPower = (power - powerMin) / (powerMax - powerMin);
		double normSla = (sla - slaMin) / (slaMax - slaMin);
		return Math.sqrt((normPower * normPower) + (normSla * normSla));
	}
}
