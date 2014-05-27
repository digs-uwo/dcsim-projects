package edu.uwo.csd.dcsim.projects.hierarchical.events;

import java.util.Map;

import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.events.MessageEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.AppStatus;

public class AppMigAcceptEvent extends MessageEvent {

	private AppStatus application;
	private Map<Integer, Host> vmHostMap;
	private AutonomicManager origin;			// Manager accepting the migration.
	
	public AppMigAcceptEvent(AutonomicManager target, AppStatus application, Map<Integer, Host> vmHostMap, AutonomicManager origin) {
		super(target);
		
		this.application = application;
		this.vmHostMap = vmHostMap;
		this.origin = origin;
	}
	
	public AppStatus getApplication() {
		return application;
	}
	
	public Map<Integer, Host> getTargetHosts() {
		return vmHostMap;
	}
	
	public AutonomicManager getOrigin() {
		return origin;
	}

}
