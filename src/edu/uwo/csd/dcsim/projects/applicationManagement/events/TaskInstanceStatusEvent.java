package edu.uwo.csd.dcsim.projects.applicationManagement.events;

import edu.uwo.csd.dcsim.application.TaskInstance;
import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class TaskInstanceStatusEvent extends MessageEvent {

	private TaskInstance taskInstance;
	private double cpuUtil;
	private double responseTime;
	
	public TaskInstanceStatusEvent(SimulationEventListener target, TaskInstance taskInstance, double cpuUtil, double responseTime) {
		super(target);
		
		this.taskInstance = taskInstance;
		this.cpuUtil = cpuUtil;
		this.responseTime = responseTime;
	}
	
	public TaskInstance getTaskInstance() {
		return taskInstance;
	}
	
	public double getCpuUtil() {
		return cpuUtil;
	}
	
	public double getResponseTime() {
		return responseTime;
	}

}
