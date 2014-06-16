package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class SurrogateAppRejectEvent extends MessageEvent {

	private int appId;
	private int vmId;
	
	public SurrogateAppRejectEvent(AutonomicManager target, int appId, int vmId) {
		super(target);
		
		this.appId = appId;
		this.vmId = vmId;
	}
	
	public int getAppId() {
		return appId;
	}
	
	public int getVmId() {
		return vmId;
	}

}
