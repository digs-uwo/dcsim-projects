package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class AwardShutdownEvent extends MessageEvent {

	public AwardShutdownEvent(SimulationEventListener target) {
		super(target);
	}

}
