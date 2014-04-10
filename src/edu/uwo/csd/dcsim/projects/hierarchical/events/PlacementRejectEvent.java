package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.events.MessageEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.ConstrainedAppAllocationRequest;

public class PlacementRejectEvent extends MessageEvent {

	private ConstrainedAppAllocationRequest request;
	private int sender;	// ID of the Rack or Cluster sending the message.
	
	public PlacementRejectEvent(AutonomicManager target, ConstrainedAppAllocationRequest request, int sender) {
		super(target);
		
		this.request = request;
		this.sender = sender;
	}
	
	public ConstrainedAppAllocationRequest getRequest() {
		return request;
	}
	
	public int getSender() {
		return sender;
	}

}
