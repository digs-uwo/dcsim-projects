package edu.uwo.csd.dcsim.projects.overloadProbability;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.common.Tuple;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.metrics.MetricCollection;

public class StressProbabilityMetrics extends MetricCollection {
	
	DescriptiveStatistics algorithmExecTime = new DescriptiveStatistics();

	public StressProbabilityMetrics(Simulation simulation) {
		super(simulation);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void completeSimulation() {

		
	}

	public void addAlgExecTime(long time) {
		algorithmExecTime.addValue(time);
	}

	@Override
	public void printDefault(Logger out) {
		out.info("-- STRESS PROBABILITY --");
		out.info("Algorithm Runtime");
		out.info("   average: " + algorithmExecTime.getMean());
		out.info("   max: " + algorithmExecTime.getMax());
		out.info("   min: " + algorithmExecTime.getMin());

		
	}

	@Override
	public List<Tuple<String, Object>> getMetricValues() {
		List<Tuple<String, Object>> metrics = new ArrayList<Tuple<String, Object>>();
	
		metrics.add(new Tuple<String, Object>("algRuntimeAvg", algorithmExecTime.getMean()));
		metrics.add(new Tuple<String, Object>("algRuntimeMax", algorithmExecTime.getMax()));
		metrics.add(new Tuple<String, Object>("algRuntimeMin", algorithmExecTime.getMin()));
		
		return metrics;
	}

}

