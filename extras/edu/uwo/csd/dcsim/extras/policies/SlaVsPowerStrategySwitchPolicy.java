package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.Collection;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.common.ObjectBuilder;
import edu.uwo.csd.dcsim.core.*;
import edu.uwo.csd.dcsim.core.metrics.AggregateMetric;
import edu.uwo.csd.dcsim.management.VMPlacementPolicy;

public class SlaVsPowerStrategySwitchPolicy implements Daemon {

	//metric names
	public static final String STRAT_SWITCH = "stratSwitch";
	public static final String STRAT_SLA_ENABLE = "stratSlaEnable";
	public static final String STRAT_POWER_ENABLE = "stratPowerEnable";
	
	public static final int WINDOW_SIZE = 10;
	
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
	double lastSlavWork = 0;					//the total SLA violated work at the last check
	double lastWork = 0;						//the total incoming work at the last check
	double lastPower = 0;						//the total power consumption at the last check
	double toPowerThreshold;					//the threshold of the utilization slope that would cause a switch to the power policy
	double toSlaThreshold;						//the threshold of the utilization slope that would cause a switch to the sla policy
	double dcCapacity = 0;
	
	private ArrayList<Double> utilList = new ArrayList<Double>();
	
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
		this.currentPolicy = builder.startingPolicy;
		this.toPowerThreshold = builder.toPowerThreshold;
		this.toSlaThreshold = builder.toSlaThreshold;
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
		double toPowerThreshold = Double.MIN_VALUE;
		double toSlaThreshold = Double.MAX_VALUE;
		
		
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
		public Builder startingPolicy(DaemonScheduler startingPolicy) { this.startingPolicy = startingPolicy; return this; }
		public Builder toPowerThreshold(double toPowerThreshold) { this.toPowerThreshold = toPowerThreshold; return this; }
		public Builder toSlaThreshold(double toSlaThreshold) { this.toSlaThreshold = toSlaThreshold; return this; }
		
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
			if (toPowerThreshold == Double.MIN_VALUE)
				throw new IllegalStateException("Must specify a toPowerThreshold threshold");
			if (toSlaThreshold == Double.MAX_VALUE)
				throw new IllegalStateException("Must specify a toSlaThreshold threshold");
			
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
		
		//calculate the total capacity of the datacenter in cpu units
		Collection<Host> hosts = dc.getHosts();
		for(Host host : hosts){
			dcCapacity += (host.getCpuCount() * host.getCoreCount() * host.getCoreCapacity());
		}
		
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
		 * Calculate the SLA metric. Values from the DC monitor are averaged over the window size. 
		 */
		double sla = dcMon.getDCsla().getMean();
		
		/*
		 * Calculate optimal power ratio metric. Values from the DC monitor are averaged over the window size.
		 */
		double power = dcMon.getDCOptimalPowerRatio().getMean();
		
		if (currentPolicy == slaPolicy) {
			//We are current running an SLA friendly policy. The goal of the SLA policy is to keep SLA below the slaNormal threshold.

			//if power exceeds powerHigh and SLA is below slaNormal, switch
			if (power > powerHigh && sla < slaNormal) {
			
				//System.out.println(simulation.getSimulationTime() + " - switch to Power");
				
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
			if (sla > slaHigh && power < powerNormal) {
			
				//System.out.println(simulation.getSimulationTime() + " - switch to SLA");
				
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
	
	private double getSlope(ArrayList<Double> list){
		double sumx = 0;
		double sumy = 0;
		double sumx2 = 0;
		double sumxy = 0;
		int startIndex = list.size() - WINDOW_SIZE;
		for(int i=0; i<WINDOW_SIZE; i++){
			sumx += i;
			sumx2 += i*i;
			sumy += list.get(startIndex+i);
			sumxy += i * list.get(startIndex+i);
		}
		int n = WINDOW_SIZE;
		double slope = ((n * sumxy) - (sumx * sumy)) / ((n * sumx2) - (sumx * sumx));
		return slope;
	}

	@Override
	public void onStop(Simulation simulation) {
		
	}

	
	
}
