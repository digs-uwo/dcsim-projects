package edu.uwo.csd.dcsim.projects.applicationManagement.events;

import edu.uwo.csd.dcsim.application.Task;
import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.management.AutonomicManager;

public class TaskInstancePlacementEvent extends Event {

	private Task task;
	private boolean failed = false;
	
	public TaskInstancePlacementEvent(AutonomicManager target, Task task) {
		super(target);
		this.task = task;
	}

	public Task getTask() {
		return task;
	}

	public void setTask(Task task) {
		this.task = task;
	}
	
	public void setFailed(boolean failed) {
		this.failed = failed;
	}
	
	public boolean isFailed() {
		return failed;
	}

}
