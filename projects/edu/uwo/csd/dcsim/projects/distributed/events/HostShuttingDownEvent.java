package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class HostShuttingDownEvent extends MessageEvent {

	private Host host;

	public HostShuttingDownEvent(SimulationEventListener target, Host host) {
		super(target);
		
		this.host = host;		
	}
	
	public Host getHost() {
		return host;
	}

}
