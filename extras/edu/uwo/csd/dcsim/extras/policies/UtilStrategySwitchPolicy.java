package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.Collection;
import org.apache.commons.math3.stat.regression.*;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.common.ObjectBuilder;
import edu.uwo.csd.dcsim.core.*;
import edu.uwo.csd.dcsim.core.metrics.AggregateMetric;
import edu.uwo.csd.dcsim.management.VMPlacementPolicy;

public class UtilStrategySwitchPolicy implements Daemon {

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
	double toPowerThreshold;					//the threshold of the utilization slope that would cause a switch to the power policy
	double toSlaThreshold;						//the threshold of the utilization slope that would cause a switch to the sla policy
	double dcCapacity = 0;						//the capacity of the data center in cpu units
	double min = Double.MAX_VALUE, max = Double.MIN_VALUE;
	
	private ArrayList<Double> utilList = new ArrayList<Double>();
	
	public UtilStrategySwitchPolicy(Builder builder) {
		this.dc = builder.dc;
		this.dcMon = builder.dcMon;
		this.slaPolicy = builder.slaPolicy;
		this.slaPlacementPolicy = builder.slaPlacementPolicy;
		this.powerPolicy = builder.powerPolicy;
		this.powerPlacementPolicy = builder.powerPlacementPolicy;
		this.currentPolicy = builder.startingPolicy;
		this.toPowerThreshold = builder.toPowerThreshold;
		this.toSlaThreshold = builder.toSlaThreshold;
	}

	public static class Builder implements ObjectBuilder<UtilStrategySwitchPolicy> {

		private DataCentre dc;
		private DCUtilizationMonitor dcMon;
		private DaemonScheduler slaPolicy;
		private DaemonScheduler powerPolicy;
		private DaemonScheduler startingPolicy =  null;
		private VMPlacementPolicy slaPlacementPolicy;
		private VMPlacementPolicy powerPlacementPolicy;
		double toPowerThreshold = Double.MIN_VALUE;
		double toSlaThreshold = Double.MAX_VALUE;
		
		
		public Builder(DataCentre dc, DCUtilizationMonitor dcMon) {
			this.dc = dc;
			this.dcMon = dcMon;
		}
		
		public Builder slaPolicy(DaemonScheduler slaPolicy, VMPlacementPolicy slaPlacementPolicy) { this.slaPolicy = slaPolicy; this.slaPlacementPolicy = slaPlacementPolicy; return this; }
		public Builder powerPolicy(DaemonScheduler powerPolicy, VMPlacementPolicy powerPlacementPolicy) { this.powerPolicy = powerPolicy; this.powerPlacementPolicy = powerPlacementPolicy; return this; }
		public Builder startingPolicy(DaemonScheduler startingPolicy) { this.startingPolicy = startingPolicy; return this; }
		public Builder toPowerThreshold(double toPowerThreshold) { this.toPowerThreshold = toPowerThreshold; return this; }
		public Builder toSlaThreshold(double toSlaThreshold) { this.toSlaThreshold = toSlaThreshold; return this; }
		
		@Override
		public UtilStrategySwitchPolicy build() {
			
			//verify that all parameters have been given
			if (slaPolicy == null)
				throw new IllegalStateException("Must specifiy an SLA friendly policy");
			if (slaPlacementPolicy == null)
				throw new IllegalStateException("Must specifiy an SLA friendly placement policy");
			if (powerPolicy == null)
				throw new IllegalStateException("Must specify a power friendly policy");
			if (powerPlacementPolicy == null)
				throw new IllegalStateException("Must specifiy an power friendly placement policy");
			if (toPowerThreshold == Double.MIN_VALUE)
				throw new IllegalStateException("Must specify a toPowerThreshold threshold");
			if (toSlaThreshold == Double.MAX_VALUE)
				throw new IllegalStateException("Must specify a toSlaThreshold threshold");
			
			return new UtilStrategySwitchPolicy(this);
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
		 * Calculate the slope of the slope of the datacenter workload line over the last
		 * WINDOW_SIZE measurements
		 */
		double utilSlope = 0;
		//utilList.add(dcMon.getDCInUse().getFirst());				//Add current datacenter workload measured in cpu units
		//utilList.add(dcMon.getDCInUse().getFirst() / dcCapacity);	//Add current datacenter workload measured in percentage utilization
		double[] dcUtil = dcMon.getDCInUse().getValues();
		for(int i=0; i<dcUtil.length; i++){
			dcUtil[i] = dcUtil[i] / dcCapacity;
		}
		utilSlope = getSlope(dcUtil);
		//System.out.println(utilSlope);
		
		if (currentPolicy == slaPolicy) {
			//We are current running an SLA friendly policy. The goal of the SLA policy is to minimise sla violations.

			//if workload change slows down to a rate less than toPowerThreshold switch to power policy
			if(utilSlope < toPowerThreshold){
				
				//switch to power policy to attempt to improve power efficiency
				enablePowerPolicy();
				
				if (simulation.isRecordingMetrics()) {
					AggregateMetric.getMetric(simulation, STRAT_SWITCH).addValue(1);
					AggregateMetric.getMetric(simulation, STRAT_POWER_ENABLE).addValue(1);
				}
			}
			
			//if workload is still increasing quickly (greater than toPowerThreshold) continue to focus on minimising sla violations
			
		} else {
			//We are currently running a power friendly policy. The goal of the power policy is to improve power efficiency.
			
			//if workload is increasing faster than toSlaThreshold, switch to sla
			if(utilSlope > toSlaThreshold){
				
				//switch to SLA policy to attempt to minimise SLA
				enableSlaPolicy();
				
				if (simulation.isRecordingMetrics()) {
					AggregateMetric.getMetric(simulation, STRAT_SWITCH).addValue(1);
					AggregateMetric.getMetric(simulation, STRAT_SLA_ENABLE).addValue(1);
				}
			}
			
			//if workload is not increasing too quickly (higher than toSlaThreshold) continue to focus on improving power efficiency
		}
	}
	
	private double getSlope(double[] list){
		SimpleRegression regression = new SimpleRegression();
		
		for(int i=0; i<list.length; i++){
			regression.addData(i, list[i]);
		}
		
		return regression.getSlope();
	}

	@Override
	public void onStop(Simulation simulation) {
		
	}

	
	
}
