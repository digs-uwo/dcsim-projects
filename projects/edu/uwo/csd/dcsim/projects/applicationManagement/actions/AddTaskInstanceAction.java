package edu.uwo.csd.dcsim.projects.applicationManagement.actions;

import edu.uwo.csd.dcsim.application.Task;
import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.core.EventCallbackListener;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.action.ManagementAction;
import edu.uwo.csd.dcsim.projects.applicationManagement.events.TaskInstancePlacementEvent;

public class AddTaskInstanceAction extends ManagementAction {

	AutonomicManager dcManager;
	Task task;
	
	public AddTaskInstanceAction(Task task, AutonomicManager dcManager) {
		this.task = task;
		this.dcManager = dcManager;
	}
	
	@Override
	public void execute(Simulation simulation, Object triggeringEntity) {
		
		TaskInstancePlacementEvent placementEvent = new TaskInstancePlacementEvent(dcManager, task);
		
		//add a callback listener to indicate this action is completed once the placement is finished
		placementEvent.addCallbackListener(new EventCallbackListener() {

			@Override
			public void eventCallback(Event e) {
				completeAction();
			}
			
		});
		simulation.sendEvent(placementEvent);
		
	}

}
