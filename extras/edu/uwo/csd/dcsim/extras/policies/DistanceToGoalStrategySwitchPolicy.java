package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.common.ObjectBuilder;
import edu.uwo.csd.dcsim.core.*;
import edu.uwo.csd.dcsim.core.metrics.AggregateMetric;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.comparator.HostComparator;
import edu.uwo.csd.dcsim.management.VMPlacementPolicy;

public class DistanceToGoalStrategySwitchPolicy implements Daemon {

	//metric names
	public static final String STRAT_SWITCH = "stratSwitch";
	public static final String STRAT_SLA_ENABLE = "stratSlaEnable";
	public static final String STRAT_POWER_ENABLE = "stratPowerEnable";
	
	DataCentre dc;								//the data centre this policy is operating on
	DCUtilizationMonitor dcMon; 				//monitor to get datacentre metrics
	DaemonScheduler slaPolicy; 					//an SLA friendly policy
	VMPlacementPolicy slaPlacementPolicy; 		//an SLA friendly placement policy
	DaemonScheduler powerPolicy;				//a power friendly policy
	VMPlacementPolicy powerPlacementPolicy; 	//a power friendly placement policy
	long lastSwitch = Long.MIN_VALUE;			//the last time a policy switch was considered
	DaemonScheduler currentPolicy;				//the current policy being enforced
	double worstSlaGoal;						// Worst SLA violations value expected (or desired).
	double lastSlavWork = 0;					//the total SLA violated work at the last check
	double lastWork = 0;						//the total incoming work at the last check
	double lastPower = 0;						//the total power consumption at the last check
	
	public DistanceToGoalStrategySwitchPolicy(Builder builder) {
		this.dc = builder.dc;
		this.dcMon = builder.dcMon;
		this.slaPolicy = builder.slaPolicy;
		this.slaPlacementPolicy = builder.slaPlacementPolicy;
		this.powerPolicy = builder.powerPolicy;
		this.powerPlacementPolicy = builder.powerPlacementPolicy;
		this.worstSlaGoal = builder.worstSlaGoal;
		this.currentPolicy = builder.startingPolicy;
	}

	public static class Builder implements ObjectBuilder<DistanceToGoalStrategySwitchPolicy> {

		private DataCentre dc;
		private DCUtilizationMonitor dcMon;
		private DaemonScheduler slaPolicy;
		private DaemonScheduler powerPolicy;
		private DaemonScheduler startingPolicy =  null;
		private VMPlacementPolicy slaPlacementPolicy;
		private VMPlacementPolicy powerPlacementPolicy;
		double worstSlaGoal = Double.MAX_VALUE;
		
		public Builder(DataCentre dc, DCUtilizationMonitor dcMon) {
			this.dc = dc;
			this.dcMon = dcMon;
		}
		
		public Builder slaPolicy(DaemonScheduler slaPolicy, VMPlacementPolicy slaPlacementPolicy) { this.slaPolicy = slaPolicy; this.slaPlacementPolicy = slaPlacementPolicy; return this; }
		public Builder powerPolicy(DaemonScheduler powerPolicy, VMPlacementPolicy powerPlacementPolicy) { this.powerPolicy = powerPolicy; this.powerPlacementPolicy = powerPlacementPolicy; return this; }
		public Builder worstSlaGoal(double worstSlaGoal) { this.worstSlaGoal = worstSlaGoal; return this; }
		public Builder startingPolicy(DaemonScheduler startingPolicy) { this.startingPolicy = startingPolicy; return this; }
		
		@Override
		public DistanceToGoalStrategySwitchPolicy build() {
			
			//verify that all parameters have been given
			if (slaPolicy == null)
				throw new IllegalStateException("Must specifiy an SLA friendly policy");
			if (slaPlacementPolicy == null)
				throw new IllegalStateException("Must specifiy an SLA friendly placement policy");
			if (powerPolicy == null)
				throw new IllegalStateException("Must specify a power friendly policy");
			if (powerPlacementPolicy == null)
				throw new IllegalStateException("Must specifiy an power friendly placement policy");
			if (worstSlaGoal == Double.MAX_VALUE)
				throw new IllegalStateException("Must specify a worstSlaGoal threshold");
			
			return new DistanceToGoalStrategySwitchPolicy(this);
		}
		
	}
	
	@Override
	public void onStart(Simulation simulation) {
		
		//ensure that the policies are running
		if (!slaPolicy.isRunning()) slaPolicy.start();
		if (!powerPolicy.isRunning()) powerPolicy.start();
		
		//if the current policy has not be set, set it to SLA by default
		if (currentPolicy == null)
			currentPolicy = slaPolicy;
		
		//enable current policy
		if (currentPolicy == slaPolicy)
			enableSlaPolicy();
		else
			enablePowerPolicy();
		
	}
	
	private void enableSlaPolicy() {
		currentPolicy = slaPolicy;
		powerPolicy.setEnabled(false);
		slaPolicy.setEnabled(true);
		dc.setVMPlacementPolicy(slaPlacementPolicy);	
	}

	private void enablePowerPolicy() {
		currentPolicy = powerPolicy;
		slaPolicy.setEnabled(false);
		powerPolicy.setEnabled(true);
		dc.setVMPlacementPolicy(powerPlacementPolicy);
	}
	
	private double calculateOptimalCpuPerPower() {
		
		//create new list of all of the Hosts in the datacentre. We create a new list as we are going to resort it
		ArrayList<Host> hosts = new ArrayList<Host>(dc.getHosts());
		
		//sort hosts by power efficiency, descending
		Collections.sort(hosts, HostComparator.EFFICIENCY);
		Collections.reverse(hosts);
		
		
		/*
		 * Calculate the theoretical optimal power consumption give the current workload.
		 * 
		 * To calculate this, we first consider the total of all CPU shares currently in use in the datacentre as a single
		 * value that can be divided arbitrarily among hosts. We set 'cpuRemaining' to this value.
		 * 
		 * We then sort all Hosts in the data centre by power efficiency, starting with the most efficient host. We remove 
		 * the number of CPU shares that the most efficient host possesses from cpuRemaining, and add the power that the host
		 * would consume given 100% load to the optimal power consumption. We then move on to the next most efficient host
		 * until cpuRemaining = 0 (all cpu has been assigned to a host).
		 * 
		 * The final host will probably not be entirely filled by the cpuRemaining still left to assign. If this is the case,
		 * we calculate what the CPU utilization of the host would be given that all of cpuRemaining is placed on the host, and use
		 * this value to calculate the host's power consumption. This power consumption is then added to the optimal power consumption. 
		 * 
		 */
		double optimalPowerConsumption = 0; //the optimal total power consumption given the current load
		double cpuRemaining = dcMon.getDCInUse().getFirst(); //the amount of CPU still to be allocated to a host
		
		int i = 0; //current position in host list
		while (cpuRemaining > 0) {
			
			//if there is more CPU left than available in the host
			if (cpuRemaining >= hosts.get(i).getTotalCpu()) {
				
				//remove the full capacity of the host from the remaining CPU
				cpuRemaining -= hosts.get(i).getTotalCpu();
				
				//add the host power consumption at 100% to the optimalPowerConsumption
				optimalPowerConsumption += hosts.get(i).getPowerModel().getPowerConsumption(1);
			} 
			//else if the host has enough capacity to satisfy all remaining CPU
			else {
				
				//calculate the host utilization
				double util = cpuRemaining / hosts.get(i).getTotalCpu();
				cpuRemaining = 0;
				
				//add the power consumption of the host at the calculated utilization level
				optimalPowerConsumption += hosts.get(i).getPowerModel().getPowerConsumption(util);
			}
			
			++i; //move to next host
		}
		
		//optimal cpu-per-power is (current CPU in use / optimal power consumption)
		return dcMon.getDCInUse().getFirst() / optimalPowerConsumption;
	}
	
	@Override
	public void run(Simulation simulation) {
			
		/*
		 * Perform hard-coded checks to switch policies. For a true strategy-tree implementation, this block should be replaced by the strategy-tree code.
		 */
		
		/* 
		 * Calculate distance to SLA goal as SLA violations over the last time 
		 * interval (*) divided by the worst SLA violations value expected (or 
		 * desired).
		 * 
		 * (*) Calculated as total amount of SLA Violated work since the last 
		 * time strategy switching ran divided by the total amount of incoming 
		 * work over the same period.
		 */
		double sla = (dcMon.getTotalSlavWork() - lastSlavWork) / (dcMon.getTotalWork() - lastWork);
		lastSlavWork = dcMon.getTotalSlavWork();
		lastWork = dcMon.getTotalWork();
		double slaDistance = sla / worstSlaGoal;
		
		/* 
		 * Calculate distance to Power goal as 1 minus the ratio of current DC 
		 * power efficiency (cpu-shares-per-watt) over optimal DC power 
		 * efficiency.
		 */
		double optimalCpuPerPower = calculateOptimalCpuPerPower();
		double currentCpuPerPower = dcMon.getDCInUse().getFirst() / dcMon.getDCPower().getFirst();
		double powerDistance = 1 - currentCpuPerPower / optimalCpuPerPower;
		
		if (slaDistance > powerDistance && currentPolicy == powerPolicy) {
			
			System.out.println(simulation.getSimulationTime() + " - switch to SLA");
			
			// Switch to SLA-friendly strategy to attempt to decrease the distance to the SLA goal.
			enableSlaPolicy();
			
			if (simulation.isRecordingMetrics()) {
				AggregateMetric.getMetric(simulation, STRAT_SWITCH).addValue(1);
				AggregateMetric.getMetric(simulation, STRAT_SLA_ENABLE).addValue(1);
			}
		}
		else if (powerDistance > slaDistance && currentPolicy == slaPolicy) {
			
			System.out.println(simulation.getSimulationTime() + " - switch to Power");
			
			// Switch to Power strategy to attempt to decrease the distance to the Power goal.
			enablePowerPolicy();
			
			if (simulation.isRecordingMetrics()) {
				AggregateMetric.getMetric(simulation, STRAT_SWITCH).addValue(1);
				AggregateMetric.getMetric(simulation, STRAT_POWER_ENABLE).addValue(1);
			}
		}
	}

	@Override
	public void onStop(Simulation simulation) {
		
	}

	
	
}
