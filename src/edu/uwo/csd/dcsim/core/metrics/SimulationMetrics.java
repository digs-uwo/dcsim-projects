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
	Map<Class<? extends MetricCollection>, MetricCollection> customMetrics = new HashMap<Class<? extends MetricCollection>, MetricCollection>();
	
	long executionTime;
	int applicationSchedulingTimedOut = 0;
	
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
	
	public int getApplicationSchedulingTimedOut() {
		return applicationSchedulingTimedOut;
	}
	
	public void setApplicationSchedulingTimedOut(int applicationSchedulingTimedOut) {
		this.applicationSchedulingTimedOut = applicationSchedulingTimedOut;
	}
	
	public void incrementApplicationSchedulingTimedOut() {
		++applicationSchedulingTimedOut;
	}
	
	@SuppressWarnings("unchecked")
	public <T extends MetricCollection> T getCustomMetricCollection(Class<T> type) {
		return (T)customMetrics.get(type);
	}
	
	public void addCustomMetricCollection(MetricCollection metricCollection) {
		customMetrics.put(metricCollection.getClass(), metricCollection);
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
		out.info("   application scheduling timed out: " + applicationSchedulingTimedOut);
		
	}
}
