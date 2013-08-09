package edu.uwo.csd.dcsim.application;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.host.Resources;

/**
 * 
 * @author Michael Tighe
 *
 */
public abstract class Task {

	int id = -1;
	int defaultInstances;
	int maxInstances;
	Resources resourceSize;
	
	public Task(int defaultInstances, Resources resourceSize) {
		this.defaultInstances = defaultInstances;
		this.maxInstances = defaultInstances;
		this.resourceSize = resourceSize;
	}
	
	public Task(int defaultInstances, int maxInstances, Resources resourceSize) {
		this.defaultInstances = defaultInstances;
		this.maxInstances = maxInstances;
		this.resourceSize = resourceSize;
	}

	/**
	 * Create an Application instance for this task
	 */
	public abstract TaskInstance createInstance();
	public abstract void removeInstance(TaskInstance instance);
	
	public abstract void startInstance(TaskInstance instance);
	public abstract void stopInstance(TaskInstance instance);
	
	/**
	 * Get the collection of Task Instances in this Task
	 * @return
	 */
	public abstract ArrayList<TaskInstance> getInstances();

	public abstract Application getApplication();
	
	public int getDefaultInstances() {
		return defaultInstances;
	}
	
	public int getMaxInstances() {
		return maxInstances;
	}

	public Resources getResourceSize() {
		return resourceSize;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}
	
}
