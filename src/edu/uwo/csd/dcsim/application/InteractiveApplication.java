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
	
	int totalCpuDemand;
	int totalCpuScheduled;

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
				instance.resourceScheduled.setCpu(instance.getVM().getMaxCpu()); //use the VMs max CPU capacity, as it may be on a different speed core than the Task size specifies 
				
				instance.setFullDemand(null); //will be calculated on first 'updateDemand' call
				instance.updateVisitRatio(); //gets the current visit ratio from the task load balancer
				
				instance.getUtilizationDeltas().clear();
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
					instance.setEffectiveServiceTime(instance.getServiceTime() * (instance.getResourceDemand().getCpu() / (float)instance.getResourceScheduled().getCpu()));
				} else {
					instance.setEffectiveServiceTime(instance.getServiceTime());
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
				
				float lastUtilization = instance.getUtilization();
				instance.setUtilization(throughput * instance.getServiceTime() * instance.getVisitRatio());
				
				instance.getUtilizationDeltas().addValue(Math.abs(lastUtilization - instance.getUtilization()));
				
				if (instance.getUtilizationDeltas().getMean() > 0.02) {
					updated = true;
				}
				
				instance.getResourceDemand().setCpu((int)((instance.getVM().getMaxCpu() * instance.getUtilization()) * (instance.getEffectiveServiceTime() / instance.getServiceTime())));
				
				if (instance.getFullDemand() == null) {
					//the first time demand is calculated, we get the full resource demand assuming full resource availability (no contention)
					instance.setFullDemand(new Resources(instance.getResourceDemand()));
				}
				
			}
		}

		//TODO remove, debugging code
//		int i = 1;
//		
//		for (InteractiveTask task : tasks) {
//			int j = 1;
//			for (InteractiveTaskInstance instance : task.getInteractiveTaskInstances()) {
//				System.out.println("App " + this.getId() + " Task " + i + "-" + j + " U=" + instance.getUtilization());
//				++j;
//			}
//			++i;
//		}
				
		//return true if utilization values changed (there was an update made), false otherwise
		return updated;
	}

	@Override
	public void advanceSimulation() {
		for (InteractiveTask task : tasks) {
			for (InteractiveTaskInstance instance : task.getInteractiveTaskInstances()) {
				totalCpuDemand += instance.getFullDemand().getCpu();
				totalCpuScheduled += instance.getResourceScheduled().getCpu();
			}
		}
	}
	

	@Override
	public int getTotalCpuDemand() {
		return totalCpuDemand;
	}

	@Override
	public int getTotalCpuScheduled() {
		return totalCpuScheduled;
	}
	
	public int calculateMaxWorkloadUtilizationLimit(float utilizationLimit) {
		return calculateMaxWorkload(Float.MAX_VALUE, utilizationLimit);
	}
	
	public int calculateMaxWorkloadResponseTimeLimit(float responseTimeLimit) {
		return calculateMaxWorkload(responseTimeLimit, Float.MAX_VALUE);
	}
	
	public int calculateMaxWorkload(float responseTimeLimit, float utilizationLimit) {
		
		float responseTime = 0;
		float throughput = 0;
		int nClients;
		ArrayList<DummyTask> dummyTasks = new ArrayList<DummyTask>();
		
		//build array of tasks, one for each task instances, assuming each task has maxTaskSize instances
		for (InteractiveTask task : tasks) {
			
			for (int i = 0; i < task.getMaxInstances(); ++i) {
				DummyTask dummy = new DummyTask(task.getNormalServiceTime(), task.getVisitRatio() / task.getMaxInstances());
				dummyTasks.add(dummy);
				
			}
		}
		
		//Use MVA algorithm to find the number of clients, terminating when the response time exceeds the limit OR a task utilization reaches 1
		for (DummyTask t : dummyTasks) {
			t.queueLength = 0;
		}
		
		boolean done = false;
		
		nClients = 0;
		while (responseTime <= responseTimeLimit && !done) {
			++nClients;
			
			responseTime = 0;
			for (DummyTask t : dummyTasks) {
				t.responseTime = t.serviceTime * (t.queueLength + 1);
				responseTime += t.responseTime * t.visits;
			}
			
			throughput = nClients / (thinkTime + responseTime);
			
			for (DummyTask t : dummyTasks) {
				t.queueLength = throughput * t.visits * t.responseTime;
				
				t.utilization = throughput * t.serviceTime * t.visits;
				
				//terminate if utilization reaches utilization limit on one task instance
				if (t.utilization >= utilizationLimit) done = true;
				
			}

		}

		return nClients - 1;
		
	}
	
	public float getThinkTime() {
		return thinkTime;
	}
	
	public void setThinkTime(float thinkTime) {
		this.thinkTime = thinkTime;
	}
	
	public float getResponseTime() {
		return responseTime;
	}
	
	public float getThroughput() {
		return throughput;
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

		private boolean used = false; //make sure this builder is only used to construct a single instance of InteractiveApplication
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
		
		public Builder task(int defaultInstances,
				int maxInstances,
				Resources resourceSize,
				float serviceTime,
				float visitRatio) {
			
			InteractiveTask task = new InteractiveTask(null, defaultInstances, maxInstances, resourceSize, serviceTime, visitRatio);
			tasks.add(task);
			
			return this;
		}
		
		public Builder task(int defaultInstances,
				int maxInstances,
				Resources resourceSize,
				float serviceTime, 
				float visitRatio,
				LoadBalancer loadBalancer) {
			
			InteractiveTask task = new InteractiveTask(null, defaultInstances, maxInstances, resourceSize, serviceTime, visitRatio, loadBalancer);
			tasks.add(task);
			
			return this;
		}
		
		@Override
		public InteractiveApplication build() {
			if (used) throw new RuntimeException("Cannot use a single InteractiveApplication.Builder to build more than one instance of InteractiveApplication");
			used = true;
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
	
	private class DummyTask {
		float serviceTime;
		float visits;
		float queueLength;
		float responseTime;
		float utilization;
		
		public DummyTask(float serviceTime, float visits) {
			this.serviceTime = serviceTime;
			this.visits = visits;
		}
	}


}
