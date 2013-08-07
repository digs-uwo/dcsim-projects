package edu.uwo.csd.dcsim.application;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.uwo.csd.dcsim.core.Simulation;

/**
 * @author Michael Tighe
 *
 */
public class InteractiveTaskInstance extends TaskInstance {

	private InteractiveTask task;
	private float effectiveServiceTime;
	private float queueLength;
	private float responseTime;
	private float throughput;
	private float utilization;
	private DescriptiveStatistics utilizationDeltas = new DescriptiveStatistics(20);
	private float visitRatio;
	
	public InteractiveTaskInstance(InteractiveTask task) {
		this.task = task;
	}

	@Override
	public void postScheduling() {
		//nothing to do
	}
	
	public float getServiceTime() {
		float serviceTime = task.getNormalServiceTime() * (task.getResourceSize().getCpu() / (float)vm.getMaxCpu());
		if (vm.isMigrating())
			serviceTime += serviceTime * Float.parseFloat(Simulation.getProperty("vmMigrationServiceTimePenalty"));
			
		return serviceTime;
	}
	
	public float getEffectiveServiceTime() {
		return effectiveServiceTime;
	}
	
	public void setEffectiveServiceTime(float effectiveServiceTime) {
		this.effectiveServiceTime = effectiveServiceTime;
	}
	
	public float getQueueLength() {
		return queueLength;
	}
	
	public void setQueueLength(float queueLength) {
		this.queueLength = queueLength;
	}
	
	public float getResponseTime() {
		return responseTime;
	}
	
	public void setResponseTime(float responseTime) {
		this.responseTime = responseTime;
	}
	
	public float getThroughput() {
		return throughput;
	}
	
	public void setThroughput(float throughput) {
		this.throughput = throughput;
	}
	
	public float getUtilization() {
		return utilization;
	}
	
	public void setUtilization(float utilization) {
		this.utilization = utilization;
	}
	
	public DescriptiveStatistics getUtilizationDeltas() {
		return utilizationDeltas;
	}
	
	public void updateVisitRatio() {
		visitRatio = task.getVisitRatio() * task.getLoadBalancer().getInstanceShare(this);
	}
	
	public float getVisitRatio() {
		return visitRatio;
	}

	@Override
	public Task getTask() {
		return task;
	}
	
}
