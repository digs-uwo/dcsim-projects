package edu.uwo.csd.dcsim.core.metrics;

import java.io.PrintStream;

import java.util.Map.Entry;

import edu.uwo.csd.dcsim.core.Simulation;

public class SimulationMetrics {

	Simulation simulation;
	HostMetrics hostMetrics;
	ApplicationMetrics applicationMetrics;
	ManagementMetrics managementMetrics;
	
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
	
	public void printDefault(PrintStream out) {
		out.println("-- HOSTS --");
		out.println("Active Hosts");
		out.println("   max: " + hostMetrics.getActiveHosts().getMax());
		out.println("   mean: " + hostMetrics.getActiveHosts().getMean());
		out.println("   min: " + hostMetrics.getActiveHosts().getMin());
		out.println("   util: " + hostMetrics.getHostUtilization().getMean());
		out.println("   total util: " + hostMetrics.getTotalUtilization().getMean());
		
		out.println("Power");
		out.println("   consumed: " + (hostMetrics.getPowerConsumption().getSum() / 3600000) + "kWh");
		out.println("   max: " + hostMetrics.getPowerConsumption().getMax());
		out.println("   mean: " + hostMetrics.getPowerConsumption().getMean());
		out.println("   min: " + hostMetrics.getPowerConsumption().getMin());
		
		out.println("VM");
		out.println("    count: " + hostMetrics.getVmCount().getSum());
		
		out.println("");
		
		out.println("-- APPLICATIONS --");
		out.println("CPU Underprovision");
		out.println("   percentage: " + applicationMetrics.getAggregateCpuUnderProvision().getSum() / applicationMetrics.getAggregateCpuDemand().getSum());
		out.println("SLA");
		out.println("    count: " + applicationMetrics.getSlaPenaltyStats().getSum());
		
		out.println("");
		
		out.println("-- MANAGEMENT --");
		out.println("Migrations");
//		for (Entry<Class<?>, WeightedMetric> entry : managementMetrics.getMigrationCount().entrySet()) {
//			out.println("    " + entry.getKey().getName() + ": " + entry.getValue().getSum());
//		}
		
	}
	
//	INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - activeHosts=14.584
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - avgDcUtil=18.452%
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - avgHostStateSize=12.535
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - avgHostUtil=56.835%
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - cpuUnderprovision=5.171%
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - cpuUnderprovisionDuration=7.466 days
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - hostTime=381.792hrs
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - maxActiveHosts=25.0
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - messageCount-HostStatusEvent=4595.0
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - migrationCount-ConsolidationPolicy=88.0
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - migrationCount-RelocationPolicy=70.0
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - minActiveHosts=14.0
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - optimalPowerEfficiencyRatio=1.4524367849439643
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - powerConsumption=75.844kWh
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - powerEfficiency=57.22 cpu/watt
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - responseTime=0.275
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - slaPenalty=395300.016
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - throughput=40.269
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - vmCount=200.0
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - vmStatesSent=57600.0
//			INFO  edu.uwo.csd.dcsim.examples.ApplicationExample      - simExecTime=7728.0
	
}
