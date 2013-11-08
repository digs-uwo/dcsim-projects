package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class ShutdownFailedEvent extends MessageEvent {

	public ShutdownFailedEvent(SimulationEventListener target) {
		super(target);

	}

}
