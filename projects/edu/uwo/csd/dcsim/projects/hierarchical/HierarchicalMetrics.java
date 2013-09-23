package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.common.Tuple;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.metrics.MetricCollection;

public class HierarchicalMetrics extends MetricCollection {

	public long migrationCountIntraRack = 0;
	public long migrationCountIntraCluster = 0;
	public long migrationCountInterCluster = 0;
	public long rejectedPlacements = 0;
	
	public HierarchicalMetrics(Simulation simulation) {
		super(simulation);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void completeSimulation() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void printDefault(Logger out) {
		out.info("-- HIERARCHICAL --");
		out.info("Migrations");
		out.info("   migrationCountIntraRack: " + migrationCountIntraRack);
		out.info("   migrationCountIntraCluster: " + migrationCountIntraCluster);
		out.info("   migrationCountInterCluster: " + migrationCountInterCluster);
		out.info("Placements");
		out.info("   rejectedPlacements: " + rejectedPlacements);
	}

	@Override
	public List<Tuple<String, Object>> getMetricValues() {
		List<Tuple<String, Object>> metrics = new ArrayList<Tuple<String, Object>>();
		
		metrics.add(new Tuple<String, Object>("migrationCountIntraRack", migrationCountIntraRack));
		metrics.add(new Tuple<String, Object>("migrationCountIntraCluster", migrationCountIntraCluster));
		metrics.add(new Tuple<String, Object>("migrationCountInterCluster", migrationCountInterCluster));
		metrics.add(new Tuple<String, Object>("rejectedPlacements", rejectedPlacements));
		
		return metrics;
	}

}
