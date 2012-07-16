package edu.uwo.csd.dcsim.extras.policies;

import edu.uwo.csd.dcsim.DCUtilizationMonitor;
import edu.uwo.csd.dcsim.common.ObjectBuilder;
import edu.uwo.csd.dcsim.core.Daemon;
import edu.uwo.csd.dcsim.core.Simulation;

public class SlaVsPowerSwitchingPolicy implements Daemon {

	DCUtilizationMonitor dcMon;
	Daemon slaPolicy;
	Daemon powerPolicy;
	long switchingInterval;
	long lastSwitch = Long.MIN_VALUE;
	Daemon currentPolicy;
	double slaHigh;
	double slaNormal;
	double powerHigh;
	double powerNormal;
	double optimalPowerPerCpu;
	double lastSlavWork = 0;
	double lastWork = 0;
	double lastPower = 0;
	
	public SlaVsPowerSwitchingPolicy(Builder builder) {
		this.dcMon = builder.dcMon;
		this.slaPolicy = builder.slaPolicy;
		this.powerPolicy = builder.powerPolicy;
		this.switchingInterval = builder.switchingInterval;
		this.slaHigh = builder.slaHigh;
		this.slaNormal = builder.slaNormal;
		this.powerHigh = builder.powerHigh;
		this.powerNormal = builder.powerNormal;
		this.optimalPowerPerCpu = builder.optimalPowerPerCpu;
	}

	public static class Builder implements ObjectBuilder<SlaVsPowerSwitchingPolicy> {

		private DCUtilizationMonitor dcMon;
		private Daemon slaPolicy;
		private Daemon powerPolicy;
		private long switchingInterval = Long.MIN_VALUE;
		double slaHigh = Double.MAX_VALUE;
		double slaNormal = Double.MIN_VALUE;
		double powerHigh = Double.MAX_VALUE;
		double powerNormal =  Double.MIN_VALUE;
		double optimalPowerPerCpu = Double.MAX_VALUE;
		
		
		public Builder(DCUtilizationMonitor dcMon) {
			this.dcMon = dcMon;
		}
		
		public Builder slaPolicy(Daemon slaPolicy) { this.slaPolicy = slaPolicy; return this; }
		public Builder powerPolicy(Daemon powerPolicy) { this.powerPolicy = powerPolicy; return this; }
		public Builder switchingInterval(long switchingInterval) { this.switchingInterval = switchingInterval; return this; }
		public Builder slaHigh(double slaHigh) { this.slaHigh = slaHigh; return this; }
		public Builder slaNormal(double slaNormal) { this.slaNormal = slaNormal; return this; }
		public Builder powerHigh(double powerHigh) { this.powerHigh = powerHigh; return this; }
		public Builder powerNormal(double powerNormal) { this.powerNormal = powerNormal; return this; }
		public Builder optimalPowerPerCpu(double optimalPowerPerCpu) { this.optimalPowerPerCpu = optimalPowerPerCpu; return this; }
		
		@Override
		public SlaVsPowerSwitchingPolicy build() {
			
			//verify that all parameters have been given
			if (slaPolicy == null)
				throw new IllegalStateException("Must specifiy an SLA friendly policy");
			if (powerPolicy == null)
				throw new IllegalStateException("Must specify a power friendly policy");
			if (switchingInterval == Long.MIN_VALUE)
				throw new IllegalStateException("Must specify a switching interval");
			if (slaHigh == Double.MAX_VALUE)
				throw new IllegalStateException("Must specify an slaHigh threshold");
			if (slaNormal == Double.MIN_VALUE)
				throw new IllegalStateException("Must specify an slaNormal threshold");
			if (powerHigh == Double.MAX_VALUE)
				throw new IllegalStateException("Must specify an powerHigh threshold");
			if (powerNormal == Double.MIN_VALUE)
				throw new IllegalStateException("Must specify an powerNormal threshold");
			if (optimalPowerPerCpu == Double.MAX_VALUE)
				throw new IllegalStateException("Must specify an optimalPowerPerCpu value");
			
			return new SlaVsPowerSwitchingPolicy(this);
		}
		
	}
	
	@Override
	public void start(Simulation simulation) {
		currentPolicy = slaPolicy; //set the current policy to the SLA policy by default
	}

	@Override
	public void run(Simulation simulation) {
		if (lastSwitch + switchingInterval <= simulation.getSimulationTime()) {
			/*
			 * Perform hard-coded checks to switch policies. For a true strategy-tree implementation, this block should be replaced by the strategy-tree code.
			 */
			
			/* 
			 * Calculate the SLA metric. This is done by getting the total amount of SLA Violated work since the last time strategy switching ran and dividing it by
			 * the total amount of incoming work over the same period.
			 */
			double sla = (dcMon.getTotalSlavWork() - lastSlavWork) / (dcMon.getTotalWork() - lastWork);
			lastSlavWork = dcMon.getTotalSlavWork();
			lastWork = dcMon.getTotalWork();
			
			
			/* 
			 * Calculate the power metric. The power metric used is the ratio of the current power-per-cpu-share to the optimal power-per-cpu-share. 
			 * The optimal value is currently a static value that should be set as the power-per-cpu-share of the most energy efficient host in the
			 * data centre when fully utilized. Note that this is not a perfect metric in a heterogeneous data centre: as load increases, the optimal
			 * power-per-cpu-share will go up as the load exceeds the capacity of the set of most energy efficient servers. Therefore, the optimal
			 * value should actually change dynamically with total load.
			 */
			double power = (dcMon.getDCPower().getFirst() / dcMon.getDCInUse().getFirst()) / optimalPowerPerCpu;
			
			if (currentPolicy == slaPolicy) {
				//We are current running an SLA friendly policy. The goal of the SLA policy is to keep SLA below the slaNormal threshold.

				//if power exceeds powerHigh and SLA is below slaNormal, switch
				if (power > powerHigh && sla < slaNormal) {
				
					//switch to power policy to attempt to reduce power to normal level
					currentPolicy = powerPolicy;
				}
				
				//if power exceeds powerHigh but SLA is still above slaNormal, remain on SLA policy to continue to reduce SLA
				
			} else {
				//We are currently running a power friendly policy. The goal of the power policy is to keep power below the powerNormal threshold.
				
				//if SLA exceeds slaHigh and power is below powerNormal, switch
				if (sla > slaHigh && power < powerNormal) {
				
					//switch to SLA policy to attempt to reduce SLA to normal level
					currentPolicy = slaPolicy;
				}
				
				//if SLA exceeds slaHigh but power is still above powerNormal, remain on power policy to continue to reduce power 
			}
		}
		
		//run the current policy
		if (currentPolicy != null)
			currentPolicy.run(simulation);
	}

	@Override
	public void stop(Simulation simulation) {
		
	}

	
	
}
