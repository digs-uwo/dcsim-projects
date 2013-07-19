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
	private float serviceTime;
	private float visitRatio;
	private InteractiveApplication application;
	private ArrayList<InteractiveTaskInstance> instances = new ArrayList<InteractiveTaskInstance>();
	
	public InteractiveTask(InteractiveApplication application,
			int defaultInstances, int minInstances, int maxInstances,
			Resources resourceSize,
			float serviceTime,
			float visitRatio) {
		super(defaultInstances, minInstances, maxInstances, resourceSize);

		this.serviceTime = serviceTime;
		this.visitRatio = visitRatio;
		
		//set default load balancer
		setLoadBalancer(new EqualShareLoadBalancer());
	}
	
	public InteractiveTask(InteractiveApplication application,
			int defaultInstances, int minInstances, int maxInstances,
			Resources resourceSize,
			float serviceTime,
			float visitRatio,
			LoadBalancer loadBalancer) {
		super(defaultInstances, minInstances, maxInstances, resourceSize);

		this.serviceTime = serviceTime;
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
	protected void startInstance(TaskInstance instance) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void stopInstance(TaskInstance instance) {
		// TODO Auto-generated method stub
		
	}
	
	public float getServiceTime() {
		return serviceTime;
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

	

	@Override
	public ArrayList<TaskInstance> getInstances() {
		ArrayList<TaskInstance> simpleInstances = new ArrayList<TaskInstance>(instances);
		return simpleInstances;
	}
	
}
