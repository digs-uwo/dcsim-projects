package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class RepairBrokenAppEvent extends MessageEvent {

	private int appId;			// Application to _repair_ (i.e., bring back together all its tasks/VMs).
	
	public RepairBrokenAppEvent(AutonomicManager target, int appId) {
		super(target);
		
		this.appId = appId;
	}
	
	public int getAppId() {
		return appId;
	}

}
