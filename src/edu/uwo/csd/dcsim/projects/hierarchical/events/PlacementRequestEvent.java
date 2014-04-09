package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.projects.hierarchical.ConstrainedAppAllocationRequest;

public class PlacementRequestEvent extends Event {

//	private InteractiveApplication application;
	private ConstrainedAppAllocationRequest request;
	private boolean failed = false;
	
	public PlacementRequestEvent(AutonomicManager target, InteractiveApplication application) {
		super(target);
		
//		this.application = application;
		this.request = new ConstrainedAppAllocationRequest(application);
	}
	
//	public InteractiveApplication getApplication() {
//		return application;
//	}
	
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
