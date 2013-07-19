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

	public InteractiveApplication(Simulation simulation) {
		super(simulation);
	}
	
	public InteractiveApplication(Builder builder) {
		super(builder.simulation);
		
		workload = builder.workload;
		
		for (InteractiveTask task : builder.tasks) {
			tasks.add(task);
			task.setApplication(this);
		}
		
	}

	@Override
	public void initializeScheduling() {
		//reset scheduled resources and demand
		//record total CPU demand for application (for use in underprovision metric)
	}
	@Override
	public void postScheduling() {
		//TODO in a batch application, this could recalculate completion time and move a completion event
	}
	
	@Override
	public boolean updateDemand() {
		//execute MVA algorithm
		//update resource demand
		//return true if utilization values changed (there was an update made), false otherwise
		return false;
	}

	@Override
	public void advanceSimulation() {
		//TODO not sure if anything actually needs to be done here. In an batch application, this might compute progress
	}
	
	@Override
	public void recordMetrics() {
		//TODO record metrics, i.e. underprovisioning % and duration, response time, throughput
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
		ArrayList<InteractiveTask> tasks = new ArrayList<InteractiveTask>();
		
		public Builder(Simulation simulation) {
			this.simulation = simulation;
		}
		
		public Builder workload(Workload workload) {
			this.workload = workload;
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
