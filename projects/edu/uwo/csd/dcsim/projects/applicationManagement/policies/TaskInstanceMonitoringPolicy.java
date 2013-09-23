package edu.uwo.csd.dcsim.projects.applicationManagement.policies;

import edu.uwo.csd.dcsim.application.*;
import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.TaskInstanceManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.events.TaskInstanceStatusEvent;

public class TaskInstanceMonitoringPolicy extends Policy {

	SimulationEventListener target;
	
	public TaskInstanceMonitoringPolicy(SimulationEventListener target) {
		addRequiredCapability(TaskInstanceManager.class);
		
		this.target = target;
	}
	
	public void execute() {
		TaskInstanceManager taskManager = manager.getCapability(TaskInstanceManager.class);
		
		TaskInstance taskInstance = taskManager.getTaskInstance();
		double cpuUtil = taskInstance.getResourceScheduled().getCpu() / (double)taskInstance.getTask().getResourceSize().getCpu();
		double responseTime = 0;
		
		if (taskInstance instanceof InteractiveTaskInstance) {
			responseTime = ((InteractiveTaskInstance)taskInstance).getResponseTime();
		}
		
		TaskInstanceStatusEvent event = new TaskInstanceStatusEvent(target, taskManager.getTaskInstance(), cpuUtil, responseTime);
		simulation.sendEvent(event);
	}
	
	@Override
	public void onInstall() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStart() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStop() {
		// TODO Auto-generated method stub
		
	}

}
