package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class AcceptOfferEvent extends MessageEvent {

	ResourceOfferEvent offer;
	
	public AcceptOfferEvent(SimulationEventListener target, ResourceOfferEvent offer) {
		super(target);
		
		this.offer = offer;
	}
	
	public ResourceOfferEvent getOffer() {
		return offer;
	}

}
