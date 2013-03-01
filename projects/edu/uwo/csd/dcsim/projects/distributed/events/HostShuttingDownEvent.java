package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class HostShuttingDownEvent extends MessageEvent {

	public HostShuttingDownEvent(SimulationEventListener target) {
		super(target);
		// TODO Auto-generated constructor stub
	}

}
