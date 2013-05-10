package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.core.SimulationEventListener;

public class ShutdownElectionEvent extends Event {

	public ShutdownElectionEvent(SimulationEventListener target) {
		super(target);
	}

}
