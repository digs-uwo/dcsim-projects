package edu.uwo.csd.dcsim.extras.policies;

import edu.uwo.csd.dcsim.DCUtilizationMonitor;
import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.common.ObjectBuilder;
import edu.uwo.csd.dcsim.core.*;
import edu.uwo.csd.dcsim.core.metrics.AggregateMetric;
import edu.uwo.csd.dcsim.management.VMPlacementPolicy;

public class SlaVsPowerStrategySwitchPolicy implements Daemon {

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
	double slaHigh;								//a threshold indicating high SLA values
	double slaNormal;							//normal SLA values fall below this threshold
	double powerHigh;							//a threshold indicating high power values
	double powerNormal;							//normal power values fall below this threshold
	double optimalPowerPerCpu;					//the optimal power-per-cpu, used as a goal for power consumption
	double optimalCpuPerPower;					//the optimal cpu-per-power, used as a goal for power consumption
	double lastSlavWork = 0;					//the total SLA violated work at the last check
	double lastWork = 0;						//the total incoming work at the last check
	double lastPower = 0;						//the total power consumption at the last check
	
	public SlaVsPowerStrategySwitchPolicy(Builder builder) {
		this.dc = builder.dc;
		this.dcMon = builder.dcMon;
		this.slaPolicy = builder.slaPolicy;
		this.slaPlacementPolicy = builder.slaPlacementPolicy;
		this.powerPolicy = builder.powerPolicy;
		this.powerPlacementPolicy = builder.powerPlacementPolicy;
		this.slaHigh = builder.slaHigh;
		this.slaNormal = builder.slaNormal;
		this.powerHigh = builder.powerHigh;
		this.powerNormal = builder.powerNormal;
		this.optimalPowerPerCpu = builder.optimalPowerPerCpu;
		this.optimalCpuPerPower = builder.optimalCpuPerPower;
		this.currentPolicy = builder.startingPolicy;
	}

	public static class Builder implements ObjectBuilder<SlaVsPowerStrategySwitchPolicy> {

		private DataCentre dc;
		private DCUtilizationMonitor dcMon;
		private DaemonScheduler slaPolicy;
		private DaemonScheduler powerPolicy;
		private DaemonScheduler startingPolicy =  null;
		private VMPlacementPolicy slaPlacementPolicy;
		private VMPlacementPolicy powerPlacementPolicy;
		double slaHigh = Double.MAX_VALUE;
		double slaNormal = Double.MIN_VALUE;
		double powerHigh = Double.MAX_VALUE;
		double powerNormal =  Double.MIN_VALUE;
		double optimalPowerPerCpu = Double.MAX_VALUE;
		double optimalCpuPerPower = Double.MIN_VALUE;
		
		
		public Builder(DataCentre dc, DCUtilizationMonitor dcMon) {
			this.dc = dc;
			this.dcMon = dcMon;
		}
		
		public Builder slaPolicy(DaemonScheduler slaPolicy, VMPlacementPolicy slaPlacementPolicy) { this.slaPolicy = slaPolicy; this.slaPlacementPolicy = slaPlacementPolicy; return this; }
		public Builder powerPolicy(DaemonScheduler powerPolicy, VMPlacementPolicy powerPlacementPolicy) { this.powerPolicy = powerPolicy; this.powerPlacementPolicy = powerPlacementPolicy; return this; }
		public Builder slaHigh(double slaHigh) { this.slaHigh = slaHigh; return this; }
		public Builder slaNormal(double slaNormal) { this.slaNormal = slaNormal; return this; }
		public Builder powerHigh(double powerHigh) { this.powerHigh = powerHigh; return this; }
		public Builder powerNormal(double powerNormal) { this.powerNormal = powerNormal; return this; }
		public Builder optimalPowerPerCpu(double optimalPowerPerCpu) { this.optimalPowerPerCpu = optimalPowerPerCpu; return this; }
		public Builder optimalCpuPerPower(double optimalCpuPerPower) { this.optimalCpuPerPower = optimalCpuPerPower; return this; }
		public Builder startingPolicy(DaemonScheduler startingPolicy) { this.startingPolicy = startingPolicy; return this; }
		
		@Override
		public SlaVsPowerStrategySwitchPolicy build() {
			
			//verify that all parameters have been given
			if (slaPolicy == null)
				throw new IllegalStateException("Must specifiy an SLA friendly policy");
			if (slaPlacementPolicy == null)
				throw new IllegalStateException("Must specifiy an SLA friendly placement policy");
			if (powerPolicy == null)
				throw new IllegalStateException("Must specify a power friendly policy");
			if (powerPlacementPolicy == null)
				throw new IllegalStateException("Must specifiy an power friendly placement policy");
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
			if (optimalCpuPerPower == Double.MIN_VALUE)
				throw new IllegalStateException("Must specify an optimalCpuPerPower value");
			
			return new SlaVsPowerStrategySwitchPolicy(this);
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
		//double power = (dcMon.getDCPower().getFirst() / dcMon.getDCInUse().getFirst()) / optimalPowerPerCpu;
		double power = optimalCpuPerPower / (dcMon.getDCInUse().getFirst() / dcMon.getDCPower().getFirst());
		
		long time = simulation.getSimulationTime();
		
		if (currentPolicy == slaPolicy) {
			//We are current running an SLA friendly policy. The goal of the SLA policy is to keep SLA below the slaNormal threshold.

			//if power exceeds powerHigh and SLA is below slaNormal, switch
			//if (power > powerHigh && sla < slaNormal) {
			if((time > 350000000 && time < 607000000) || (time > 690000000)){
			
				System.out.println(simulation.getSimulationTime() + " - switch to Power");
				
				//switch to power policy to attempt to reduce power to normal level
				enablePowerPolicy();
				
				if (simulation.isRecordingMetrics()) {
					AggregateMetric.getMetric(simulation, STRAT_SWITCH).addValue(1);
					AggregateMetric.getMetric(simulation, STRAT_POWER_ENABLE).addValue(1);
				}
			}
			
			//if power exceeds powerHigh but SLA is still above slaNormal, remain on SLA policy to continue to reduce SLA
			
		} else {
			//We are currently running a power friendly policy. The goal of the power policy is to keep power below the powerNormal threshold.
			
			//if SLA exceeds slaHigh and power is below powerNormal, switch
			//if (sla > slaHigh && power < powerNormal) {
			if((time > 260000000 && time < 350000000) || (time > 607000000 && time < 690000000)){
			
				System.out.println(simulation.getSimulationTime() + " - switch to SLA");
				
				//switch to SLA policy to attempt to reduce SLA to normal level
				enableSlaPolicy();
				
				if (simulation.isRecordingMetrics()) {
					AggregateMetric.getMetric(simulation, STRAT_SWITCH).addValue(1);
					AggregateMetric.getMetric(simulation, STRAT_SLA_ENABLE).addValue(1);
				}
			}
			
			//if SLA exceeds slaHigh but power is still above powerNormal, remain on power policy to continue to reduce power 
		}
	}

	@Override
	public void onStop(Simulation simulation) {
		
	}

	
	
}
