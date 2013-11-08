package edu.uwo.csd.dcsim.projects.applicationManagement.capabilities;

import edu.uwo.csd.dcsim.application.TaskInstance;
import edu.uwo.csd.dcsim.management.capabilities.ManagerCapability;

public class TaskInstanceManager extends ManagerCapability {

	private TaskInstance taskInstance;
	
	public TaskInstanceManager(TaskInstance taskInstance) {
		this.taskInstance = taskInstance;
	}
	
	public TaskInstance getTaskInstance() {
		return taskInstance;
	}
	
}
