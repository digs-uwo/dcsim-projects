package edu.uwo.csd.dcsim.application;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.application.loadbalancer.LoadBalancer;
import edu.uwo.csd.dcsim.application.workload.*;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.metrics.AvgValueMetric;
import edu.uwo.csd.dcsim.core.metrics.CpuUnderprovisionDurationMetric;
import edu.uwo.csd.dcsim.core.metrics.CpuUnderprovisionMetric;
import edu.uwo.csd.dcsim.common.*;
import edu.uwo.csd.dcsim.host.Resources;

/**
 * @author Michael Tighe
 *
 */
public class InteractiveApplication extends Application {

	public static final String CPU_UNDERPROVISION_METRIC = "cpuUnderprovision";
	public static final String CPU_UNDERPROVISION_DURATION_METRIC = "cpuUnderprovisionDuration";
	
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
				
				float lastUtilization = instance.getUtilization();
				instance.setUtilization(throughput * task.getServiceTime() * instance.getVisitRatio());
				
				instance.getUtilizationDeltas().addValue(Math.abs(lastUtilization - instance.getUtilization()));
				
				if (instance.getUtilizationDeltas().getMean() > 0.02) {
					updated = true;
				}
				
				instance.getResourceDemand().setCpu((int)((task.getResourceSize().getCpu() * instance.getUtilization()) * (instance.getEffectiveServiceTime() / task.getServiceTime())));
				
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
		//TODO not sure if anything actually needs to be done here. In an batch application, this might compute progress
	}
	
	@Override
	public void recordMetrics() {
		//TODO record metrics, i.e. underprovisioning % and duration, response time, throughput
		
		int cpuDemand = 0;
		int cpuScheduled = 0;
		for (InteractiveTask task : tasks) {
			for (InteractiveTaskInstance instance : task.getInteractiveTaskInstances()) {
				cpuDemand += instance.getFullDemand().getCpu();
				cpuScheduled += instance.getResourceScheduled().getCpu();
			}
		}

		//record the CPU underprovision metrics
		if (cpuDemand > cpuScheduled) {
			CpuUnderprovisionMetric.getMetric(simulation, CPU_UNDERPROVISION_METRIC).addSlaVWork(cpuDemand - cpuScheduled);
		}
		CpuUnderprovisionMetric.getMetric(simulation, CPU_UNDERPROVISION_METRIC).addWork(cpuDemand);
		
		CpuUnderprovisionDurationMetric.getMetric(simulation, CPU_UNDERPROVISION_DURATION_METRIC).addSlaViolationTime(simulation.getElapsedTime());
		
		//TODO change
		AvgValueMetric.getMetric(simulation, "responseTime").addValue(responseTime);
		AvgValueMetric.getMetric(simulation, "throughput").addValue(throughput);
		
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
				Resources resourceSize,
				float serviceTime,
				float visitRatio) {
			
			InteractiveTask task = new InteractiveTask(null, defaultInstances, resourceSize, serviceTime, visitRatio);
			tasks.add(task);
			
			return this;
		}
		
		public Builder task(int defaultInstances,
				Resources resourceSize,
				float serviceTime,
				float visitRatio,
				LoadBalancer loadBalancer) {
			
			InteractiveTask task = new InteractiveTask(null, defaultInstances, resourceSize, serviceTime, visitRatio, loadBalancer);
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

}
