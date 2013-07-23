package edu.uwo.csd.dcsim.application.loadbalancer;

import edu.uwo.csd.dcsim.application.TaskInstance;

/**
 * 
 * @author Michael Tighe
 *
 */
public class EqualShareLoadBalancer extends LoadBalancer {

	@Override
	public float getInstanceShare(TaskInstance taskInstance) {
		
		if (task == null) {
			throw new RuntimeException("Load Balancer called with null Task");
		}
		
		return 1 / (float)task.getInstances().size();
	}



}
