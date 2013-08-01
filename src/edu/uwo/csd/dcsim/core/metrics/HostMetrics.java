package edu.uwo.csd.dcsim.core.metrics;

import java.util.*;

import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.vm.VmAllocation;

public class HostMetrics extends MetricCollection {

	WeightedMetric powerConsumption = new WeightedMetric(this);
	WeightedMetric powerEfficiency = new WeightedMetric(this);
	
	WeightedMetric activeHosts = new WeightedMetric(this);
	WeightedMetric hostUtilization = new WeightedMetric(this);
	WeightedMetric totalUtilization = new WeightedMetric(this);
	
	WeightedMetric vmCount = new WeightedMetric(this);
	
	public HostMetrics(Simulation simulation) {
		super(simulation);
	}
	
	public void recordHostMetrics(Collection<Host> hosts) {
		double currentPowerConsumption = 0;
		double currentActiveHosts = 0;
		double currentHostUtilization = 0;
		double currentTotalInUse = 0;
		double currentTotalCapacity = 0;
		double currentTotalUtilization;
		double currentVmCount = 0;
		
		updateTimeWeights();
		
		for (Host host : hosts) {
			currentPowerConsumption += host.getCurrentPowerConsumption();
			
			for (VmAllocation vmAlloc : host.getVMAllocations()) {
				if (vmAlloc.getVm() != null) ++currentVmCount;
			}
			
			if (host.getState() == Host.HostState.ON) {
				++currentActiveHosts;
				currentHostUtilization += host.getResourceManager().getCpuUtilization();
			}
			
			currentTotalInUse += host.getResourceManager().getCpuInUse();
			currentTotalCapacity += host.getResourceManager().getTotalCpu();
			
		}
		
		currentHostUtilization = currentHostUtilization / currentActiveHosts;
		currentTotalUtilization = currentTotalInUse / currentTotalCapacity;
		
		powerConsumption.add(currentPowerConsumption / 1000); //divide by 1000 because weighted time is in ms, not seconds
		powerEfficiency.add(currentTotalInUse / currentPowerConsumption);
		activeHosts.add(currentActiveHosts);
		hostUtilization.add(currentHostUtilization);
		totalUtilization.add(currentTotalUtilization);
		vmCount.add(currentVmCount);
		
	}
	
	public ArrayList<Double> getTimeWeights() {
		return timeWeights;
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
	
	public WeightedMetric getVmCount() {
		return vmCount;
	}

	@Override
	public void completeSimulation() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void completeTimeStep() {
		// TODO Auto-generated method stub
		
	}

}
