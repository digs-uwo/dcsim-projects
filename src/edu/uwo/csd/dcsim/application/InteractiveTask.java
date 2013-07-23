package edu.uwo.csd.dcsim.application;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.application.loadbalancer.*;
import edu.uwo.csd.dcsim.host.Resources;

/**
 * A tier of InteractiveApplications 
 * 
 * @author Michael Tighe
 *
 */
public class InteractiveTask extends Task {

	private LoadBalancer loadBalancer;
	private float normalServiceTime;
	private float visitRatio;
	private InteractiveApplication application;
	private ArrayList<InteractiveTaskInstance> instances = new ArrayList<InteractiveTaskInstance>();
	
	public InteractiveTask(InteractiveApplication application,
			int defaultInstances,
			Resources resourceSize,
			float normalServiceTime,
			float visitRatio) {
		super(defaultInstances, resourceSize);

		this.normalServiceTime = normalServiceTime;
		this.visitRatio = visitRatio;
		
		//set default load balancer
		setLoadBalancer(new EqualShareLoadBalancer());
	}
	
	public InteractiveTask(InteractiveApplication application,
			int defaultInstances,
			Resources resourceSize,
			float normalServiceTime,
			float visitRatio,
			LoadBalancer loadBalancer) {
		super(defaultInstances, resourceSize);

		this.normalServiceTime = normalServiceTime;
		this.visitRatio = visitRatio;
		setLoadBalancer(loadBalancer);
	}
	
	/**
	 * Get the load balancer for this tier
	 * @return
	 */
	public LoadBalancer getLoadBalancer() {
		return loadBalancer;
	}
	
	/**
	 * Set the load balancer for this tier
	 * @param loadBalancer
	 */
	public void setLoadBalancer(LoadBalancer loadBalancer) {
		this.loadBalancer = loadBalancer;
		loadBalancer.setTask(this);
	}
	
	@Override
	public TaskInstance createInstance() {
		InteractiveTaskInstance instance = new InteractiveTaskInstance(this);
		instances.add(instance);
		startInstance(instance);
		return instance;
	}

	@Override
	public void removeInstance(TaskInstance instance) {
		if (instances.contains(instance)) {
			instances.remove(instance);
			stopInstance(instance);
		} else {
			throw new RuntimeException("Attempted to remove instance from task that does not contain it");
		}
	}

	@Override
	public void startInstance(TaskInstance instance) {
		//ensure that workload is started
		application.getWorkload().setEnabled(true);
	}

	@Override
	public void stopInstance(TaskInstance instance) {
		// TODO ...remove from Task/Load Balancer? When is this even used?
		
	}
	
	public float getNormalServiceTime() {
		return normalServiceTime;
	}
	
	public float getVisitRatio() {
		return visitRatio;
	}

	@Override
	public Application getApplication() {
		return application;
	}
	
	public void setApplication(InteractiveApplication application) {
		this.application = application;
	}

	public ArrayList<InteractiveTaskInstance> getInteractiveTaskInstances() {
		return instances;
	}

	@Override
	public ArrayList<TaskInstance> getInstances() {
		ArrayList<TaskInstance> simpleInstances = new ArrayList<TaskInstance>(instances);
		return simpleInstances;
	}
	
}
