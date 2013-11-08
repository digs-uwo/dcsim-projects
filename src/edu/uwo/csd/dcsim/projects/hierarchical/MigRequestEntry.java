package edu.uwo.csd.dcsim.projects.hierarchical;

import edu.uwo.csd.dcsim.management.*;

public class MigRequestEntry {

	// These two pieces of information are used to identify the migration request.
	private VmStatus vm;					// VM to migrate.
	private AutonomicManager origin;		// Manager of the Rack requesting the migration.
	
	// Additional info stored by a RackManager that sends a migration request.
	private HostData host;					// Source Host.
	
	// Additional info stored by the DCManager, and by a ClusterManager when the migration request was sent by one of its Racks.
	private int sender;					// ID of the Rack or Cluster sending the request.
	// timestamp ?
	// attempts ?
	
	public MigRequestEntry(VmStatus vm, AutonomicManager origin, HostData host) {
		this.vm = vm;
		this.origin = origin;
		this.host = host;
		
		this.sender = 0;
	}
	
	public MigRequestEntry(VmStatus vm, AutonomicManager origin, int sender) {
		this.vm = vm;
		this.origin = origin;
		this.sender = sender;
		
		this.host = null;
	}
	
	public VmStatus getVm() { return vm; }
	
	public HostData getHost() { return host; }
	
	public AutonomicManager getOrigin() { return origin; }
	
	public int getSender() { return sender; }

}
