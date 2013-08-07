package edu.uwo.csd.dcsim.core.metrics;

import java.io.PrintStream;

import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.core.Simulation;

public class SimulationMetrics {

	Simulation simulation;
	HostMetrics hostMetrics;
	ApplicationMetrics applicationMetrics;
	ManagementMetrics managementMetrics;
	
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
	
	public void printDefault(PrintStream out) {
		
		hostMetrics.printDefault(out);
		out.println("");
		applicationMetrics.printDefault(out);
		out.println("");
		managementMetrics.printDefault(out);
		out.println("");

		out.println("-- SIMULATION --");
		out.println("   execution time: " + SimTime.toHumanReadable(getExecutionTime()));
		out.println("   simulated time: " + SimTime.toHumanReadable(simulation.getDuration()));
		out.println("   metric recording start: " + SimTime.toHumanReadable(simulation.getMetricRecordStart()));
		out.println("   metric recording duration: " + SimTime.toHumanReadable(simulation.getDuration() - simulation.getMetricRecordStart()));
	
	}
}
