package edu.uwo.csd.dcsim.application;

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
	private float[] prevUtilization = {0, 0};
	private float visitRatio;
	
	public InteractiveTaskInstance(InteractiveTask task) {
		this.task = task;
	}

	@Override
	public void postScheduling() {
		//nothing to do
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
	
	public float[] getPrevUtilization() {
		return prevUtilization;
	}
	
	public void setPrevUtilization(float prevUtilization[]) {
		this.prevUtilization = prevUtilization;
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
