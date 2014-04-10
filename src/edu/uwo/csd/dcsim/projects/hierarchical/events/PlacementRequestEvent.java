package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.projects.hierarchical.ConstrainedAppAllocationRequest;

public class PlacementRequestEvent extends Event {

	private ConstrainedAppAllocationRequest request;
	private boolean failed = false;
	
	public PlacementRequestEvent(AutonomicManager target, ConstrainedAppAllocationRequest request) {
		super(target);
		
		this.request = request;
	}
	
	public ConstrainedAppAllocationRequest getRequest() {
		return request;
	}
	
	public void setFailed(boolean failed) {
		this.failed = failed;
	}
	
	public boolean isFailed() {
		return failed;
	}

}
