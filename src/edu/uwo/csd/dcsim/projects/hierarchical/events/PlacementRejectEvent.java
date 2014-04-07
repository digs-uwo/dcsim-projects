package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class PlacementRejectEvent extends MessageEvent {

	private PlacementRequestEvent request;
	private int sender;						// ID of the Rack or Cluster sending the message.
	
	public PlacementRejectEvent(AutonomicManager target, PlacementRequestEvent request, int sender) {
		super(target);
		
		this.request = request;
		this.sender = sender;
	}
	
	public PlacementRequestEvent getPlacementRequest() { return request; }
	
	public int getSender() { return sender; }

}
