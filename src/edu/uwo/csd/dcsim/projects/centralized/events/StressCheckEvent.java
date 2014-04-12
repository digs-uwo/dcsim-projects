package edu.uwo.csd.dcsim.projects.centralized.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class StressCheckEvent extends MessageEvent {

	private int hostId;
	
	public StressCheckEvent(SimulationEventListener target, int hostId) {
		super(target);
		this.hostId = hostId;
	}

	public int getHostId() {
		return hostId;
	}

}
