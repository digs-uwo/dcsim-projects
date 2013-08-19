package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.events.MessageEvent;
import edu.uwo.csd.dcsim.vm.VMAllocationRequest;

public class VmPlacementRejectEvent extends MessageEvent {

	private VMAllocationRequest request;	// VM Placement request.
	private int sender;					// ID of the Rack or Cluster sending the message.
	
	public VmPlacementRejectEvent(AutonomicManager target, VMAllocationRequest request, int sender) {
		super(target);
		
		this.request = request;
		this.sender = sender;
	}
	
	public VMAllocationRequest getVmAllocationRequest() { return request; }
	
	public int getSender() { return sender; }

}
