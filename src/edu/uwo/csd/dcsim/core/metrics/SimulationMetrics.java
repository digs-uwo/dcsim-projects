package edu.uwo.csd.dcsim.core.metrics;

import java.util.*;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.core.Simulation;

public class SimulationMetrics {

	Simulation simulation;
	HostMetrics hostMetrics;
	ApplicationMetrics applicationMetrics;
	ManagementMetrics managementMetrics;
	Map<String, MetricCollection> customMetrics = new HashMap<String, MetricCollection>();
	
	long executionTime;
	
	public SimulationMetrics(Simulation simulation) {
		this.simulation = simulation;
		
		hostMetrics = new HostMetrics(simulation);
		applicationMetrics = new ApplicationMetrics(simulation);
		managementMetrics = new ManagementMetrics(simulation);
	}
	
	public HostMetrics getHostMetrics() {
		return hostMetrics;
	}
	
	public ApplicationMetrics getApplicationMetrics() {
		return applicationMetrics;
	}
	
	public ManagementMetrics getManagementMetrics() {
		return managementMetrics;
	}
	
	public void completeSimulation() {
		hostMetrics.completeSimulation();
		applicationMetrics.completeSimulation();
		managementMetrics.completeSimulation();
	}
	
	public void setExecutionTime(long time) {
		executionTime = time;
	}
	
	public long getExecutionTime() {
		return executionTime;
	}
	
	public MetricCollection getCustomMetricCollection(String name) {
		return customMetrics.get(name);
	}
	
	public void addCustomMetricCollection(String name, MetricCollection metricCollection) {
		customMetrics.put(name, metricCollection);
	}
	
	public void printDefault(Logger out) {
		
		hostMetrics.printDefault(out);
		out.info("");
		applicationMetrics.printDefault(out);
		out.info("");
		managementMetrics.printDefault(out);
		out.info("");
		
		for (MetricCollection metrics : customMetrics.values()) {
			metrics.printDefault(out);
			out.info("");
		}

		out.info("-- SIMULATION --");
		out.info("   execution time: " + SimTime.toHumanReadable(getExecutionTime()));
		out.info("   simulated time: " + SimTime.toHumanReadable(simulation.getDuration()));
		out.info("   metric recording start: " + SimTime.toHumanReadable(simulation.getMetricRecordStart()));
		out.info("   metric recording duration: " + SimTime.toHumanReadable(simulation.getDuration() - simulation.getMetricRecordStart()));
	
	}
}
