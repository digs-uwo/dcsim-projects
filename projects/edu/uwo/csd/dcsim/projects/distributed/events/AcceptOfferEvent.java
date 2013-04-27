package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class AcceptOfferEvent extends MessageEvent {

	public AcceptOfferEvent(SimulationEventListener target) {
		super(target);
	}

}
