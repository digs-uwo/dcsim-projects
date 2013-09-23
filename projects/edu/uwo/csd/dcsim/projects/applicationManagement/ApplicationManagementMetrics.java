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
	
	public int managerExecutions = 0;
	public int scaleUpRelief = 0;
	public int scaleDownStress = 0;
	public int scaleDownStressMinor = 0;
	public int scaleDownShutdown = 0;
	public int stressMigration = 0;
	public int underMigration = 0;
	public int scaleUp = 0;
	public int scaleDown = 0;
	public int shutdownAttempts = 0;
	public int shutdowns = 0;
	public int emptyShutdown = 0;
	
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
		out.info("Policy Actions");
		out.info("   managerExecutions: " + managerExecutions);
		out.info("   scaleUpRelief: " + scaleUpRelief);
		out.info("   scaleDownStress: " + scaleDownStress);
		out.info("   scaleDownStressMinor: " + scaleDownStressMinor);
		out.info("   scaleDownShutdown: " + scaleDownShutdown);
		out.info("   stressMigration: " + stressMigration);
		out.info("   underMigration: " + underMigration);
		out.info("   scaleUp: " + scaleUp);
		out.info("   scaleDown: " + scaleDown);
		out.info("   shutdownAttempts: " + shutdownAttempts);
		out.info("   shutdowns: " + shutdowns);
		out.info("   emptyShutdown: " + emptyShutdown);
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
