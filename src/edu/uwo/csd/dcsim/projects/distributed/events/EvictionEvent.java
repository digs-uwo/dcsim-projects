package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.projects.distributed.Eviction;

public class EvictionEvent extends Event {

	Eviction eviction;
	
	public EvictionEvent(SimulationEventListener target, Eviction eviction) {
		super(target);
		
		this.eviction = eviction;
	}
	
	public Eviction getEviction() {
		return eviction;
	}

}
