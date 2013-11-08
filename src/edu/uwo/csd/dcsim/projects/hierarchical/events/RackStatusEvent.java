package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.events.MessageEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.RackStatus;

public class RackStatusEvent extends MessageEvent {

	private RackStatus status;
	
	public RackStatusEvent(SimulationEventListener target, RackStatus status) {
		super(target);
		
		this.status = status;
	}
	
	public RackStatus getRackStatus() {
		return status;
	}

}
