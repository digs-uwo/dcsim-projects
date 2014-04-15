package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.events.MessageEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.AppStatus;

public class AppMigRejectEvent extends MessageEvent {

	private AppStatus application;			// Application to migrate.
	private AutonomicManager origin;		// Manager of the Rack requesting the migration.
	private int sender;					// ID of the Rack or Cluster sending (or forwarding) the request.
	
	public AppMigRejectEvent(AutonomicManager target, AppStatus application, AutonomicManager origin, int sender) {
		super(target);
		
		this.application = application;
		this.origin = origin;
		this.sender = sender;
	}
	
	public AppStatus getApplication() { return application; }
	
	public AutonomicManager getOrigin() { return origin; }
	
	public int getSender() { return sender; }

}
