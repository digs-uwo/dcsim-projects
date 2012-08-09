package edu.uwo.csd.dcsim.extras.policies;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.common.ObjectBuilder;
import edu.uwo.csd.dcsim.core.*;
import edu.uwo.csd.dcsim.core.metrics.AggregateMetric;
import edu.uwo.csd.dcsim.management.VMPlacementPolicy;

public class DistanceToGoalStrategySwitchPolicy implements Daemon {

	//metric names
	public static final String STRAT_SWITCH = "stratSwitch";
	public static final String STRAT_SLA_ENABLE = "stratSlaEnable";
	public static final String STRAT_POWER_ENABLE = "stratPowerEnable";
	
	DataCentre dc;								// Data centre this policy is operating on.
	DCUtilizationMonitor dcMon; 				// Monitor to get datacentre metrics.
	DaemonScheduler slaPolicy; 					// SLA-friendly strategy.
	VMPlacementPolicy slaPlacementPolicy; 		// SLA-friendly VM Placement policy.
	DaemonScheduler powerPolicy;				// Power-friendly strategy.
	VMPlacementPolicy powerPlacementPolicy; 	// Power-friendly VM Placement policy.
	long lastSwitch = Long.MIN_VALUE;			// Last time a strategy switch was considered.
	DaemonScheduler currentPolicy;				// Current strategy being enforced.
	double worstSlaGoal;						// Worst SLA violations Goal. Expected (or desired) worst value.
	double worstPowerEffCo;						// Worst Power Efficiency Coefficient, as percentage (0,1) of the current Optimal Power Efficiency metric.
	
	public DistanceToGoalStrategySwitchPolicy(Builder builder) {
		this.dc = builder.dc;
		this.dcMon = builder.dcMon;
		this.slaPolicy = builder.slaPolicy;
		this.slaPlacementPolicy = builder.slaPlacementPolicy;
		this.powerPolicy = builder.powerPolicy;
		this.powerPlacementPolicy = builder.powerPlacementPolicy;
		this.worstSlaGoal = builder.worstSlaGoal;
		this.worstPowerEffCo = builder.worstPowerEffCo;
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
		double worstPowerEffCo = Double.MAX_VALUE;
		
		public Builder(DataCentre dc, DCUtilizationMonitor dcMon) {
			this.dc = dc;
			this.dcMon = dcMon;
		}
		
		public Builder slaPolicy(DaemonScheduler slaPolicy, VMPlacementPolicy slaPlacementPolicy) { this.slaPolicy = slaPolicy; this.slaPlacementPolicy = slaPlacementPolicy; return this; }
		public Builder powerPolicy(DaemonScheduler powerPolicy, VMPlacementPolicy powerPlacementPolicy) { this.powerPolicy = powerPolicy; this.powerPlacementPolicy = powerPlacementPolicy; return this; }
		public Builder worstSlaGoal(double worstSlaGoal) { this.worstSlaGoal = worstSlaGoal; return this; }
		public Builder worstPowerEffCo(double worstPowerEffCo) { this.worstPowerEffCo = worstPowerEffCo; return this; }
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
				throw new IllegalStateException("Must specify a Worst SLA Goal value");
			if (worstPowerEffCo == Double.MAX_VALUE)
				throw new IllegalStateException("Must specify a Worst Power Efficiency Coefficient");
			
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
	
	@Override
	public void run(Simulation simulation) {
			
		/*
		 * Perform hard-coded checks to switch policies. For a true strategy-tree implementation, this block should be replaced by the strategy-tree code.
		 */
		
		/* 
		 * Calculate distance to SLA goal as SLA violations over the last time 
		 * interval (*) divided by the Worst SLA violations Goal.
		 * 
		 * (*) Values from the DC monitor are averaged over the window size.
		 */
		double slaDistance = dcMon.getDCsla().getMean() / worstSlaGoal;
		
		/* 
		 * Calculate distance to Power goal.
		 */
		double currentPowerEff = dcMon.getDCPowerEfficiency().getMean();
		double optimalPowerEff = dcMon.getDCOptimalPowerEfficiency().getMean();
		double worstPowerEff = optimalPowerEff * worstPowerEffCo;
		double powerDistance = 1 - (currentPowerEff - worstPowerEff) / (optimalPowerEff - worstPowerEff);
		
		/* 
		 * Calculate distance to Power goal as 1 minus the ratio of current DC 
		 * power efficiency (cpu-shares-per-watt) over optimal DC power 
		 * efficiency.
		 */
		//powerDistance = 1 - currentPowerEff / optimalPowerEff;
		
		
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
