package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.events.MessageEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.ClusterStatus;

public class ClusterStatusEvent extends MessageEvent {

	private ClusterStatus status;
	
	public ClusterStatusEvent(SimulationEventListener target, ClusterStatus status) {
		super(target);
		
		this.status = status;
	}
	
	public ClusterStatus getClusterStatus() {
		return status;
	}

}
