package edu.uwo.csd.dcsim.projects.hierarchical.events;

import java.util.Map;

import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.events.MessageEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.AppStatus;

public class AppMigAcceptEvent extends MessageEvent {

	private AppStatus application;
	private Map<Integer, Host> vmHostMap;
	
	public AppMigAcceptEvent(AutonomicManager target, AppStatus application, Map<Integer, Host> vmHostMap) {
		super(target);
		
		this.application = application;
		this.vmHostMap = vmHostMap;
	}
	
	public AppStatus getApplication() {
		return application;
	}
	
	public Map<Integer, Host> getTargetHosts() {
		return vmHostMap;
	}

}
