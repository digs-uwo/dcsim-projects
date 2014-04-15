package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.Map;

import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.VmStatus;

public class MigRequestEntry {

	// Entry ID: the tuple < origin , application > or < origin , vm > is used to identify an entry.
	private AutonomicManager origin;				// Manager of the Rack requesting the migration.
	private AppStatus application;					// Application to migrate.
	private VmStatus vm;							// VM to migrate.
	
	private HostData host = null;			// Source Host. Additional info stored by a RackManager that sends a migration request.
	private Map<Integer, HostData> sourceHosts;
	
	private int sender = 0;						// ID of the Rack or Cluster sending the request. Additional info stored by the DCManager or a ClusterManager when the migration request was sent by one of its Racks.
	
	// timestamp ?
	// attempts ?
	
	public MigRequestEntry(AppStatus application, AutonomicManager origin, Map<Integer, HostData> sourceHosts) {
		this.application = application;
		this.origin = origin;
		this.sourceHosts = sourceHosts;
	}
	
	public MigRequestEntry(AppStatus application, AutonomicManager origin, int sender) {
		this.application = application;
		this.origin = origin;
		this.sender = sender;
	}
	
	public MigRequestEntry(VmStatus vm, AutonomicManager origin, HostData host) {
		this.vm = vm;
		this.origin = origin;
		this.host = host;
	}
	
	public MigRequestEntry(VmStatus vm, AutonomicManager origin, int sender) {
		this.vm = vm;
		this.origin = origin;
		this.sender = sender;
	}
	
	public AutonomicManager getOrigin() { return origin; }
	
	public AppStatus getApplication() { return application; }
	
	public VmStatus getVm() { return vm; }
	
	public HostData getHost() { return host; }
	
	public Map<Integer, HostData> getSourceHosts() { return sourceHosts; }
	
	public int getSender() { return sender; }

}
