package edu.uwo.csd.dcsim.projects.distributed.actions;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.action.ManagementAction;
import edu.uwo.csd.dcsim.projects.distributed.events.UpdatePowerStateListEvent;

public class UpdatePowerStateListAction extends ManagementAction {

	SimulationEventListener target;
	private ArrayList<Host> poweredOffHosts;
	
	public UpdatePowerStateListAction(SimulationEventListener target, ArrayList<Host> poweredOffHosts) {
		this.target = target;
		this.poweredOffHosts = poweredOffHosts;
	}
	
	@Override
	public void execute(Simulation simulation, Object triggeringEntity) {
		simulation.sendEvent(new UpdatePowerStateListEvent(target, poweredOffHosts));
		
		completeAction();
	}
	
}
