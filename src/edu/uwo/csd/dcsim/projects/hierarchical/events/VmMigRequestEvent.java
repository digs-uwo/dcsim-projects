package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class VmMigRequestEvent extends MessageEvent {

	private VmStatus vm;					// VM to migrate.
	private AutonomicManager origin;		// Manager of the Rack requesting the migration.
	private int sender;					// ID of the Rack or Cluster sending the request.
	
	public VmMigRequestEvent(AutonomicManager target, VmStatus vm, AutonomicManager origin, int sender) {
		super(target);
		
		this.vm = vm;
		this.origin = origin;
		this.sender = sender;
	}
	
	public VmStatus getVm() { return vm; }
	
	public AutonomicManager getOrigin() { return origin; }
	
	public int getSender() { return sender; }

}
