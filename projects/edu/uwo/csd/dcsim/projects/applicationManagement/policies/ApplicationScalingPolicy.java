package edu.uwo.csd.dcsim.projects.applicationManagement.policies;

import edu.uwo.csd.dcsim.application.*;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.projects.applicationManagement.actions.AddTaskInstanceAction;
import edu.uwo.csd.dcsim.projects.applicationManagement.actions.RemoveTaskInstanceAction;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.ApplicationManager;

public class ApplicationScalingPolicy extends Policy {

	private AutonomicManager dcManager;
	
	public ApplicationScalingPolicy(AutonomicManager dcManager) {
		addRequiredCapability(ApplicationManager.class);
		this.dcManager = dcManager;
	}
	
	public void execute() {
		
		ApplicationManager appManager = manager.getCapability(ApplicationManager.class);
		Application app = appManager.getApplication();
		
		System.out.println("Managing App#" + app.getId());
		
		if (!app.getSla().evaluate()) {
			//add an Instance to each Task that isn't at maximum
			for (Task task : app.getTasks()) {
				if (task.getInstances().size() < task.getMaxInstances()) {
					AddTaskInstanceAction action = new AddTaskInstanceAction(task, dcManager);
					action.execute(simulation, this);
					System.out.println("Adding Instance to Task " + app.getId() + "-" + task.getId());
				}
			}
		} else {
			//look for an underutilized task to scale down (choose lowest utilization task)
			Task targetTask = null;
			double targetUtil = Double.MAX_VALUE;
			
			for (Task task : app.getTasks()) {
				if (task.getInstances().size() > 1) {
					double utilization = 0;
					for (TaskInstance instance : task.getInstances()) {
						utilization += instance.getResourceScheduled().getCpu() / task.getResourceSize().getCpu();
					}
					utilization = utilization / task.getInstances().size();
					
					if (utilization / (task.getInstances().size() - 1) < 0.9) {
						if (utilization < targetUtil) {
							targetUtil = utilization;
							targetTask = task;
						}
					}
				}
			}
			
			if (targetTask != null) {
				
				double targetHostUtil = -1;
				TaskInstance targetInstance = null;
				
				//choose a task instance to shut down (instance on host with highest utilization to reduce overload risk)
				for (TaskInstance instance : targetTask.getInstances()) {
					double util = instance.getVM().getVMAllocation().getHost().getResourceManager().getCpuUtilization();
					if (util > targetHostUtil) {
						targetHostUtil = util;
						targetInstance = instance;
					}
				}
				
				RemoveTaskInstanceAction action = new RemoveTaskInstanceAction(targetInstance);
				action.execute(simulation, this);
				
			}
		}
		
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
