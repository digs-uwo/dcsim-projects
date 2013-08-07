package edu.uwo.csd.dcsim.core.metrics;

import java.io.PrintStream;
import java.util.*;

import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Host;

public class HostMetrics extends MetricCollection {

	WeightedMetric powerConsumption = new WeightedMetric();
	WeightedMetric powerEfficiency = new WeightedMetric();
	
	WeightedMetric activeHosts = new WeightedMetric();
	WeightedMetric hostUtilization = new WeightedMetric();
	WeightedMetric totalUtilization = new WeightedMetric();
	
	public HostMetrics(Simulation simulation) {
		super(simulation);
	}
	
	public void recordHostMetrics(Collection<Host> hosts) {
		double currentPowerConsumption = 0;
		double currentActiveHosts = 0;
		double currentTotalInUse = 0;
		double currentTotalCapacity = 0;
		double currentTotalUtilization;
		
		for (Host host : hosts) {
			currentPowerConsumption += host.getCurrentPowerConsumption();
			
			if (host.getState() == Host.HostState.ON) {
				++currentActiveHosts;
				hostUtilization.add(host.getResourceManager().getCpuUtilization(), simulation.getElapsedTime());
			}
			
			currentTotalInUse += host.getResourceManager().getCpuInUse();
			currentTotalCapacity += host.getResourceManager().getTotalCpu();
		}
			
		currentTotalUtilization = currentTotalInUse / currentTotalCapacity;
		
		powerConsumption.add(currentPowerConsumption, simulation.getElapsedSeconds());
		powerEfficiency.add(currentTotalInUse / currentPowerConsumption, simulation.getElapsedSeconds());
		activeHosts.add(currentActiveHosts, simulation.getElapsedTime());
		totalUtilization.add(currentTotalUtilization, simulation.getElapsedTime());
		
	}
	
	public WeightedMetric getPowerConsumption() {
		return powerConsumption;
	}
	
	public WeightedMetric getPowerEfficiency() {
		return powerEfficiency;
	}
	
	public WeightedMetric getActiveHosts() {
		return activeHosts;
	}
	
	public WeightedMetric getHostUtilization() {
		return hostUtilization;
	}
	
	public WeightedMetric getTotalUtilization() {
		return totalUtilization;
	}

	@Override
	public void completeSimulation() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void printDefault(PrintStream out) {
		out.println("-- HOSTS --");
		out.println("Active Hosts");
		out.println("   max: " + Utility.roundDouble(getActiveHosts().getMax(), Simulation.getMetricPrecision()));
		out.println("   mean: " + Utility.roundDouble(getActiveHosts().getMean(), Simulation.getMetricPrecision()));
		out.println("   min: " + Utility.roundDouble(getActiveHosts().getMin(), Simulation.getMetricPrecision()));
		out.println("   util: " + Utility.roundDouble(Utility.toPercentage(getHostUtilization().getMean()), Simulation.getMetricPrecision()) + "%");
		out.println("   total util: " + Utility.roundDouble(Utility.toPercentage(getTotalUtilization().getMean()), Simulation.getMetricPrecision()) + "%");
		
		out.println("Power");
		out.println("   consumed: " + Utility.roundDouble(Utility.toKWH(getPowerConsumption().getSum()), Simulation.getMetricPrecision()) + "kWh");
		out.println("   max: " + Utility.roundDouble(getPowerConsumption().getMax(), Simulation.getMetricPrecision()) + "Ws");
		out.println("   mean: " + Utility.roundDouble(getPowerConsumption().getMean(), Simulation.getMetricPrecision()) + "Ws");
		out.println("   min: " + Utility.roundDouble(getPowerConsumption().getMin(), Simulation.getMetricPrecision()) + "Ws");
		out.println("   efficiency: " + Utility.roundDouble(getPowerEfficiency().getMean(), Simulation.getMetricPrecision()) + "cpu/watt");
		
	}
}
