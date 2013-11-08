package edu.uwo.csd.dcsim.projects.distributed.actions;

import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.action.ManagementAction;
import edu.uwo.csd.dcsim.projects.distributed.events.AcceptOfferEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.ResourceOfferEvent;

public class AcceptOfferAction extends ManagementAction {

	SimulationEventListener target;
	ResourceOfferEvent offer;
	
	public AcceptOfferAction(SimulationEventListener target, ResourceOfferEvent offer) {
		this.target = target;
		this.offer = offer;
	}
	
	@Override
	public void execute(Simulation simulation, Object triggeringEntity) {
		simulation.sendEvent(new AcceptOfferEvent(target, offer));
		
		completeAction();
	}

}
