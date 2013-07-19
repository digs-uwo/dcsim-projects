package edu.uwo.csd.dcsim.application;

/**
 * @author Michael Tighe
 *
 */
public class InteractiveTaskInstance extends TaskInstance {

	private InteractiveTask task;
	private float effectiveServiceTime;
	private float responseTime;
	private float throughput;
	private float utilization;
	private float prevUtilization;
	private float visitRatio; //could be retrieved on demand from Task (load balancer)
	
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
	
	public float getResponseTime() {
		return responseTime;
	}
	
	public float getThroughput() {
		return throughput;
	}
	
	public float getUtilization() {
		return utilization;
	}
	
	public float getPrevUtilization() {
		return prevUtilization;
	}
	
	public float getVisitRatio() {
		return visitRatio;
	}

	@Override
	public Task getTask() {
		return task;
	}
	
}
