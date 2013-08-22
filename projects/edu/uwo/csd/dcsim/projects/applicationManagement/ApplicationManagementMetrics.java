package edu.uwo.csd.dcsim.projects.applicationManagement;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.common.Tuple;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.metrics.MetricCollection;

public class ApplicationManagementMetrics extends MetricCollection {

	public int instancesAdded = 0;
	public int instancesRemoved = 0;
	public int instancePlacementsFailed = 0;
	
	public ApplicationManagementMetrics(Simulation simulation) {
		super(simulation);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void completeSimulation() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void printDefault(Logger out) {
		out.info("-- APPLICATION MANAGEMENT --");
		out.info("Autoscaling");
		out.info("   added: " + instancesAdded);
		out.info("   removed: " + instancesRemoved);
		out.info("   failed: " + instancePlacementsFailed);
	}

	@Override
	public List<Tuple<String, Object>> getMetricValues() {
		List<Tuple<String, Object>> metrics = new ArrayList<Tuple<String, Object>>();
	
		metrics.add(new Tuple<String, Object>("instancesAdded", instancesAdded));
		metrics.add(new Tuple<String, Object>("instancesRemoved", instancesRemoved));
		metrics.add(new Tuple<String, Object>("instancePlacementsFailed", instancePlacementsFailed));
		
		return metrics;
	}

}
