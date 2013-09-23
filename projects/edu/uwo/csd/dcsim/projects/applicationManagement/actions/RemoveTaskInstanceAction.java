package edu.uwo.csd.dcsim.projects.applicationManagement.actions;

import edu.uwo.csd.dcsim.application.TaskInstance;
import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.core.EventCallbackListener;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.action.ManagementAction;
import edu.uwo.csd.dcsim.management.events.ShutdownVmEvent;

public class RemoveTaskInstanceAction extends ManagementAction {

	private TaskInstance taskInstance;
	
	public RemoveTaskInstanceAction(TaskInstance taskInstance) {
		this.taskInstance = taskInstance;
	}
	
	@Override
	public void execute(Simulation simulation, Object triggeringEntity) {

		Host host = taskInstance.getVM().getVMAllocation().getHost();
		
		taskInstance.getTask().removeInstance(taskInstance);
		
		ShutdownVmEvent shutdownEvent = new ShutdownVmEvent(host.getAutonomicManager(), host.getId(), taskInstance.getVM().getId());
		
		//add a callback listener to indicate this action is completed once the placement is finished
		shutdownEvent.addCallbackListener(new EventCallbackListener() {

			@Override
			public void eventCallback(Event e) {
				completeAction();
			}
			
		});
		simulation.sendEvent(shutdownEvent);

	}

}
