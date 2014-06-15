package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class SurrogateAppMigrateEvent extends MessageEvent {

	private int appId;
	private int vmId;
	private Host targetHost;
	
	public SurrogateAppMigrateEvent(AutonomicManager target, int appId, int vmId, Host targetHost) {
		super(target);
		
		this.appId = appId;
		this.vmId = vmId;
		this.targetHost = targetHost;
	}
	
	public int getAppId() {
		return appId;
	}
	
	public int getVmId() {
		return vmId;
	}
	
	public Host getTargetHost() {
		return targetHost;
	}

}
