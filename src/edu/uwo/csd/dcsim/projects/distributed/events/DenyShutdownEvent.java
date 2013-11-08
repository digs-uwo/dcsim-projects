package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class DenyShutdownEvent extends MessageEvent {

	public DenyShutdownEvent(SimulationEventListener target) {
		super(target);
	}

}
