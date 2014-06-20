package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.Collection;
import java.util.HashMap;

import edu.uwo.csd.dcsim.application.Task;
import edu.uwo.csd.dcsim.application.Task.TaskConstraintType;
import edu.uwo.csd.dcsim.common.HashCodeUtil;

/**
 * @author Gaston Keller
 *
 */
public class TaskData {

	private int id = -1;
	private int appId = -1;
	private TaskConstraintType constraintType;
	private HashMap<Long, TaskInstanceData> instances = new HashMap<Long, TaskInstanceData>();
	
	private final int hashCode;
	
	public TaskData(Task task, int appId) {
		id = task.getId();
		this.appId = appId;
		constraintType = task.getConstraintType();
		
		// init hashCode
		hashCode = generateHashCode();
	}
	
	private TaskData(TaskData original) {
		id = original.id;
		appId = original.appId;
		constraintType = original.constraintType;
		instances = new HashMap<Long, TaskInstanceData>(original.instances);
		hashCode = original.hashCode;
	}
	
	public TaskData copy() {
		return new TaskData(this);
	}
	
	public void addInstance(TaskInstanceData instance) {
		instances.put(instance.getId(), instance);
	}
	
	public TaskInstanceData removeInstance(long instanceId) {
		return instances.remove(instanceId);
	}
	
	public void setHostingVm(long taskInstanceId, int vmId) {
		TaskInstanceData instance = new TaskInstanceData(taskInstanceId, id, appId);
		instance.setHostingVm(vmId);
		instances.put(taskInstanceId, instance);
	}
	
	public int getId() {
		return id;
	}
	
	public int getAppId() {
		return appId;
	}
	
	public TaskConstraintType getConstraintType() {
		return constraintType;
	}
	
	/**
	 * Returns the only instance of this task. If the task has more than one instance, the method throws a RuntimeException.
	 */
	public TaskInstanceData getInstance() {
		if (instances.size() == 1)
			return instances.values().iterator().next();
		else
			throw new RuntimeException(String.format("[TaskData] This method was called on Task #%d of App #%d, which has more than one instance.", id, appId));
	}
	
	public TaskInstanceData getInstance(long instanceId) {
		return instances.get(instanceId);
	}
	
	public Collection<TaskInstanceData> getInstances() {
		return instances.values();
	}
	
	@Override
	public int hashCode() {
		return hashCode;
	}
	
	private int generateHashCode() {
		int result = HashCodeUtil.SEED;
		result = HashCodeUtil.hash(result, id);
		return result;
	}

}
