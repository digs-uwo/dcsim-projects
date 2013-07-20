package edu.uwo.csd.dcsim.application;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.application.loadbalancer.LoadBalancer;
import edu.uwo.csd.dcsim.application.workload.*;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.common.*;
import edu.uwo.csd.dcsim.host.Resources;

/**
 * @author Michael Tighe
 *
 */
public class InteractiveApplication extends Application {

	private Workload workload;
	private ArrayList<InteractiveTask> tasks = new ArrayList<InteractiveTask>();
	float thinkTime = 0;
	float responseTime = 0;
	float throughput = 0;

	public InteractiveApplication(Simulation simulation) {
		super(simulation);
	}
	
	public InteractiveApplication(Builder builder) {
		super(builder.simulation);
		
		workload = builder.workload;
		thinkTime = builder.thinkTime;
		
		for (InteractiveTask task : builder.tasks) {
			tasks.add(task);
			task.setApplication(this);
		}
		
	}

	@Override
	public void initializeScheduling() {
		//reset scheduled resources and demand
		for (InteractiveTask task : tasks) {
			for (InteractiveTaskInstance instance : task.getInteractiveTaskInstances()) {
				instance.resourceScheduled = new Resources(task.getResourceSize());
				instance.setFullDemand(null); //will be calculated on first 'updateDemand' call
				instance.updateVisitRatio(); //gets the current visit ratio from the task load balancer
			}
		}		
	}
	
	@Override
	public void postScheduling() {
		//TODO in a batch application, this could recalculate completion time and move a completion event
	}
	
	@Override
	public boolean updateDemand() {
		
		int nClients = workload.getWorkOutputLevel();
		
		//calculate effective service time
		for (InteractiveTask task : tasks) {
			for (InteractiveTaskInstance instance : task.getInteractiveTaskInstances()) {
				if (instance.getResourceDemand().getCpu() > instance.getResourceScheduled().getCpu()) {
					instance.setEffectiveServiceTime(task.getServiceTime() * (instance.getResourceDemand().getCpu() / (float)instance.getResourceScheduled().getCpu()));
				} else {
					instance.setEffectiveServiceTime(task.getServiceTime());
				}
			}
		}
		
		//execute MVA algorithm
		for (InteractiveTask task : tasks) {
			for (InteractiveTaskInstance instance : task.getInteractiveTaskInstances()) {
				instance.setQueueLength(0);
			}
		}
		
		for (int i = 1; i <= nClients; ++i) {
			
			responseTime = 0;
			for (InteractiveTask task : tasks) {
				for (InteractiveTaskInstance instance : task.getInteractiveTaskInstances()) {
					instance.setResponseTime(instance.getEffectiveServiceTime() * (instance.getQueueLength() + 1));
					
					responseTime += instance.getResponseTime() * instance.getVisitRatio();
				}
			}
			
			throughput = i / (thinkTime + responseTime);
			
			for (InteractiveTask task : tasks) {
				for (InteractiveTaskInstance instance : task.getInteractiveTaskInstances()) {
					instance.setQueueLength(throughput * instance.getVisitRatio() * instance.getResponseTime());
				}
			}
			
		}
		
		boolean updated = false;
		for (InteractiveTask task : tasks) {
			for (InteractiveTaskInstance instance : task.getInteractiveTaskInstances()) {
				instance.setThroughput(throughput * instance.getVisitRatio());
				
				instance.setPrevUtilization(instance.getUtilization());
				instance.setUtilization(throughput * task.getServiceTime() * instance.getVisitRatio());
				if (instance.getPrevUtilization() != instance.getUtilization()) {
					updated = true;
				}
				
				instance.getResourceDemand().setCpu((int)((task.getResourceSize().getCpu() * instance.getUtilization()) * (instance.getEffectiveServiceTime() / task.getServiceTime())));
				
				if (instance.getFullDemand() == null) {
					//the first time demand is calculated, we get the full resource demand assuming full resource availability (no contention)
					instance.setFullDemand(new Resources(instance.getResourceDemand()));
				}
				
			}
		}
		
		//return true if utilization values changed (there was an update made), false otherwise
		return updated;
	}

	@Override
	public void advanceSimulation() {
		//TODO not sure if anything actually needs to be done here. In an batch application, this might compute progress
	}
	
	@Override
	public void recordMetrics() {
		//TODO record metrics, i.e. underprovisioning % and duration, response time, throughput
	}
	
	public float getThinkTime() {
		return thinkTime;
	}
	
	public void setThinkTime(float thinkTime) {
		this.thinkTime = thinkTime;
	}
	
	/**
	 * Get the Workload for this Service
	 * @return
	 */
	public Workload getWorkload() {
		return workload;
	}
	
	/**
	 * Set the Workload for this Service
	 * @param workload
	 */
	public void setWorkload(Workload workload) {
		this.workload = workload;
	}
	
	public static class Builder implements ObjectBuilder<Application> {

		private Simulation simulation;
		private Workload workload;
		private float thinkTime;
		ArrayList<InteractiveTask> tasks = new ArrayList<InteractiveTask>();
		
		public Builder(Simulation simulation) {
			this.simulation = simulation;
		}
		
		public Builder workload(Workload workload) {
			this.workload = workload;
			return this;
		}
		
		public Builder thinkTime(float thinkTime) {
			this.thinkTime = thinkTime;
			return this;
		}
		
		public Builder task(int defaultInstances, int minInstances, int maxInstances,
				Resources resourceSize,
				float serviceTime,
				float visitRatio) {
			
			InteractiveTask task = new InteractiveTask(null, defaultInstances, minInstances, maxInstances, resourceSize, serviceTime, visitRatio);
			tasks.add(task);
			
			return this;
		}
		
		public Builder task(int defaultInstances, int minInstances, int maxInstances,
				Resources resourceSize,
				float serviceTime,
				float visitRatio,
				LoadBalancer loadBalancer) {
			
			InteractiveTask task = new InteractiveTask(null, defaultInstances, minInstances, maxInstances, resourceSize, serviceTime, visitRatio, loadBalancer);
			tasks.add(task);
			
			return this;
		}
		
		@Override
		public InteractiveApplication build() {
			return new InteractiveApplication(this);
		}
		
	}

	public void addTask(InteractiveTask task) {
		tasks.add(task);
		task.setApplication(this);
	}
	
	@Override
	public ArrayList<Task> getTasks() {
		ArrayList<Task> simpleTasks = new ArrayList<Task>(tasks);
		return simpleTasks;
	}

}
