package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class RejectOfferEvent extends MessageEvent {

	public RejectOfferEvent(SimulationEventListener target) {
		super(target);
	}

}
