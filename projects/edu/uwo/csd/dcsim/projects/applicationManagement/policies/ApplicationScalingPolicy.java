package edu.uwo.csd.dcsim.projects.applicationManagement.policies;

import edu.uwo.csd.dcsim.application.*;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.projects.applicationManagement.actions.AddTaskInstanceAction;
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
