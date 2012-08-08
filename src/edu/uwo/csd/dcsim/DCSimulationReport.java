package edu.uwo.csd.dcsim;

import java.util.*;

import org.apache.commons.math3.stat.descriptive.*;
import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.core.metrics.Metric;

public class DCSimulationReport {

	private static Logger logger = Logger.getLogger(DCSimulationReport.class);
	
	private String name;
	private Collection<DCSimulationTask> tasks;
	private Map<String, DescriptiveStatistics> metricStats = new HashMap<String, DescriptiveStatistics>();
	
	public DCSimulationReport(List<DCSimulationTask> tasks) {
		this(tasks.get(0).getName(), tasks);
	}
	
	public DCSimulationReport(String name, Collection<DCSimulationTask> tasks) {
		this.tasks = tasks;
		this.name = name;
		
		/*
		 * Build descriptive statistics for each metric.
		 * Metrics present in some tasks but not in others are considered to have a value of 0.
		 */
		
		//Initialize the metric stats map with all metric names found in any task. This requires checking every task for metrics.
		for (DCSimulationTask task : tasks) {
			for (Metric metric : task.getResults()) {
				if (!metricStats.containsKey(metric.getName())) {
					metricStats.put(metric.getName(), new DescriptiveStatistics());
				}
			}
		}
		
		//Iterate through all tasks and add their metric values
		for (DCSimulationTask task : tasks) {
			for (String metricName : metricStats.keySet()) {
				//set the value to 0 by default, in case the metric is not present in this task
				double val = 0;
				
				//find the metric value
				for (Metric metric : task.getResults()) {
					if (metric.getName() == metricName) {
						val = metric.getValue();
					}
				}
				
				//add the value to the metric statistics
				metricStats.get(metricName).addValue(val);
			}
		}
	}

	public void logResults() {
		
		logger.info("Results for " + name + " (" + tasks.size() + " repetitions)");
		
		//sort metrics by name
		ArrayList<String> metricNames = new ArrayList<String>(metricStats.keySet());
		Collections.sort(metricNames);
		
		//output metrics in alphabetical order
		for (String metricName : metricNames) {
			DescriptiveStatistics stats = metricStats.get(metricName);
			logger.info(metricName + ": " + 
					stats.getMean() + " [" + 
					stats.getStandardDeviation() + "], Range [" + 
					stats.getMin() + "," + 
					stats.getMax() + "]");
		}
		
	}
	
}
