package edu.uwo.csd.dcsim.core.metrics;

import java.io.PrintStream;
import java.util.*;

import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.vm.VmAllocation;

public class HostMetrics extends MetricCollection {

	WeightedMetric powerConsumption = new WeightedMetric();
	WeightedMetric powerEfficiency = new WeightedMetric();
	
	WeightedMetric activeHosts = new WeightedMetric();
	WeightedMetric hostUtilization = new WeightedMetric();
	WeightedMetric totalUtilization = new WeightedMetric();
	
	long vmCount = 0;
	
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
			
			for (VmAllocation vmAlloc : host.getVMAllocations()) {
				if (vmAlloc.getVm() != null) ++vmCount;
			}
			
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
	
	public long getVmCount() {
		return vmCount;
	}

	@Override
	public void completeSimulation() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void printDefault(PrintStream out) {
		out.println("-- HOSTS --");
		out.println("Active Hosts");
		out.println("   max: " + getActiveHosts().getMax());
		out.println("   mean: " + getActiveHosts().getMean());
		out.println("   min: " + getActiveHosts().getMin());
		out.println("   util: " + Utility.toPercentage(getHostUtilization().getMean()) + "%");
		out.println("   total util: " + Utility.toPercentage(getTotalUtilization().getMean()) + "%");
		
		out.println("Power");
		out.println("   consumed: " + (Utility.toKWH(getPowerConsumption().getSum())) + "kWh");
		out.println("   max: " + getPowerConsumption().getMax() + "Ws");
		out.println("   mean: " + getPowerConsumption().getMean() + "Ws");
		out.println("   min: " + getPowerConsumption().getMin() + "Ws");
		out.println("   efficiency: " + getPowerEfficiency().getMean() + "cpu/watt");
		
		out.println("VM");
		out.println("    count: " + getVmCount());
		
	}
}
