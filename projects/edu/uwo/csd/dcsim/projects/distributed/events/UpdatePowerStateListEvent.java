package edu.uwo.csd.dcsim.projects.distributed.events;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class UpdatePowerStateListEvent extends MessageEvent {

	private ArrayList<Host> poweredOffHosts;
	
	public UpdatePowerStateListEvent(SimulationEventListener target, ArrayList<Host> poweredOffHosts) {
		super(target);
		this.poweredOffHosts = poweredOffHosts;
	}
	
	public ArrayList<Host> getPoweredOffHosts() {
		return poweredOffHosts;
	}

}
