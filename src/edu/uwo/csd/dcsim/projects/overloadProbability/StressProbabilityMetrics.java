package edu.uwo.csd.dcsim.projects.overloadProbability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.common.Tuple;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.metrics.MetricCollection;
import edu.uwo.csd.dcsim.host.Host;

public class StressProbabilityMetrics extends MetricCollection {
	
	DescriptiveStatistics algorithmExecTime = new DescriptiveStatistics();
	DescriptiveStatistics filteredVms = new DescriptiveStatistics();
	long timeOver95 = 0;
	long timeOver90 = 0;
	
	public StressProbabilityMetrics(Simulation simulation) {
		super(simulation);
		// TODO Auto-generated constructor stub
	}
	
	public void recordHostMetrics(Collection<Host> hosts) {
		for (Host host : hosts) {
			if (host.getResourceManager().getCpuUtilization() > 0.9) timeOver90 += simulation.getElapsedTime();
			if (host.getResourceManager().getCpuUtilization() > 0.95) timeOver95 += simulation.getElapsedTime();
		}
	}

	@Override
	public void completeSimulation() {

		
	}

	public void addAlgExecTime(long time) {
		algorithmExecTime.addValue(time);
	}
	
	public void addFilteredVms(int n) {
		filteredVms.addValue(n);
	}

	@Override
	public void printDefault(Logger out) {
		out.info("-- STRESS PROBABILITY --");
		out.info("Algorithm Runtime");
		out.info("   average: " + algorithmExecTime.getMean());
		out.info("   max: " + algorithmExecTime.getMax());
		out.info("   min: " + algorithmExecTime.getMin());
		out.info("Filtered VMs");
		out.info("   total: " + filteredVms.getSum());
		out.info("   average: " + filteredVms.getMean());
		out.info("   max: " + filteredVms.getMax());
		out.info("   min: " + filteredVms.getMin());
		out.info("-- HOST OVERUTIL --");
		out.info("   > 90: " + SimTime.toHumanReadable(timeOver90));
		out.info("   > 95: " + SimTime.toHumanReadable(timeOver95));
	}

	@Override
	public List<Tuple<String, Object>> getMetricValues() {
		List<Tuple<String, Object>> metrics = new ArrayList<Tuple<String, Object>>();
	
		metrics.add(new Tuple<String, Object>("algRuntimeAvg", algorithmExecTime.getMean()));
		metrics.add(new Tuple<String, Object>("algRuntimeMax", algorithmExecTime.getMax()));
		metrics.add(new Tuple<String, Object>("algRuntimeMin", algorithmExecTime.getMin()));
		
		metrics.add(new Tuple<String, Object>("filteredVmsSum", filteredVms.getSum()));
		metrics.add(new Tuple<String, Object>("filteredVmsMean", filteredVms.getMean()));
		metrics.add(new Tuple<String, Object>("filteredVmsMax", filteredVms.getMax()));
		metrics.add(new Tuple<String, Object>("filteredVmsMin", filteredVms.getMin()));
		
		metrics.add(new Tuple<String, Object>("timeOver90", SimTime.toHours(timeOver90)));
		metrics.add(new Tuple<String, Object>("timeOver95", SimTime.toHours(timeOver95)));
		
		return metrics;
	}

}

