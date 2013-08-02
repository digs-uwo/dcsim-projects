package edu.uwo.csd.dcsim.core.metrics;

import java.io.PrintStream;

import java.util.Map.Entry;

import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

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
	
	public void completeTimeStep() {
		hostMetrics.completeTimeStep();
		applicationMetrics.completeTimeStep();
		managementMetrics.completeTimeStep();
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
		out.println("-- HOSTS --");
		out.println("Active Hosts");
		out.println("   max: " + hostMetrics.getActiveHosts().getMax());
		out.println("   mean: " + hostMetrics.getActiveHosts().getMean());
		out.println("   min: " + hostMetrics.getActiveHosts().getMin());
		out.println("   util: " + hostMetrics.getHostUtilization().getMean());
		out.println("   total util: " + hostMetrics.getTotalUtilization().getMean());
		
		out.println("Power");
		out.println("   consumed: " + (Utility.toKWH(hostMetrics.getPowerConsumption().getSum())) + "kWh");
		out.println("   max: " + hostMetrics.getPowerConsumption().getMax());
		out.println("   mean: " + hostMetrics.getPowerConsumption().getMean());
		out.println("   min: " + hostMetrics.getPowerConsumption().getMin());
		out.println("   efficiency: " + hostMetrics.getPowerEfficiency().getMean());
		
		out.println("VM");
		out.println("    count: " + hostMetrics.getVmCount());
		
		out.println("");
		
		out.println("-- APPLICATIONS --");
		out.println("CPU Underprovision");
		out.println("   percentage: " + Utility.toPercentage(applicationMetrics.getAggregateCpuUnderProvision().getSum() / applicationMetrics.getAggregateCpuDemand().getSum()));
		out.println("SLA");
		out.println("  aggregate penalty");
		out.println("    total: " + applicationMetrics.getAggregateSlaPenalty().getSum());
		out.println("    max: " + applicationMetrics.getAggregateSlaPenalty().getMax());
		out.println("    mean: " + applicationMetrics.getAggregateSlaPenalty().getMean());
		out.println("    min: " + applicationMetrics.getAggregateSlaPenalty().getMin());
		out.println("  per application penalty");
		out.println("    mean: " + applicationMetrics.getSlaPenaltyStats().getMean());
		out.println("    stdev: " + applicationMetrics.getSlaPenaltyStats().getStandardDeviation());
		out.println("    max: " + applicationMetrics.getSlaPenaltyStats().getMax());
		out.println("    95th: " + applicationMetrics.getSlaPenaltyStats().getPercentile(95));
		out.println("    75th: " + applicationMetrics.getSlaPenaltyStats().getPercentile(75));
		out.println("    50th: " + applicationMetrics.getSlaPenaltyStats().getPercentile(50));
		out.println("    25th: " + applicationMetrics.getSlaPenaltyStats().getPercentile(25));
		out.println("    min: " + applicationMetrics.getSlaPenaltyStats().getMin());
		out.println("Response Time");
		out.println("    max: " + applicationMetrics.getAggregateResponseTime().getMax());
		out.println("    mean: " + applicationMetrics.getAggregateResponseTime().getMean());
		out.println("    min: " + applicationMetrics.getAggregateResponseTime().getMin());
		out.println("Throughput");
		out.println("    max: " + applicationMetrics.getAggregateThroughput().getMax());
		out.println("    mean: " + applicationMetrics.getAggregateThroughput().getMean());
		out.println("    min: " + applicationMetrics.getAggregateThroughput().getMin());
		out.println("Spawning");
		out.println("   spawned: " + applicationMetrics.getApplicationsSpawned());
		out.println("   shutdown: " + applicationMetrics.getApplicationsShutdown());
		out.println("   failed placement: " + applicationMetrics.getApplicationPlacementsFailed());
		
		out.println("");
		
		out.println("-- MANAGEMENT --");
		out.println("Messages");
		for (Entry<Class<? extends MessageEvent>, Long> entry : managementMetrics.getMessageCount().entrySet()) {
			out.println("    " + entry.getKey().getName() + ": " + entry.getValue());
		}
		out.println("Message BW");
		for (Entry<Class<? extends MessageEvent>, Double> entry : managementMetrics.getMessageBw().entrySet()) {
			out.println("    " + entry.getKey().getName() + ": " + entry.getValue());
		}
		out.println("Migrations");
		for (Entry<Class<?>, Long> entry : managementMetrics.getMigrationCount().entrySet()) {
			out.println("    " + entry.getKey().getName() + ": " + entry.getValue());
		}

		out.println("");
		
		out.println("-- SIMULATION --");
		out.println("   execution time: " + SimTime.toHumanReadable(getExecutionTime()));
	
	}
}
