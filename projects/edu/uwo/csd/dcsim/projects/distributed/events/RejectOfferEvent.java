package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class RejectOfferEvent extends MessageEvent {

	ResourceOfferEvent offer;
	
	public RejectOfferEvent(SimulationEventListener target, ResourceOfferEvent offer) {
		super(target);
		
		this.offer = offer;
	}
	
	public ResourceOfferEvent getOffer() {
		return offer;
	}

}
