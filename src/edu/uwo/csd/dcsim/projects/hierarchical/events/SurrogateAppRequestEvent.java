package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class SurrogateAppRequestEvent extends MessageEvent {

	private int appId;
	
	public SurrogateAppRequestEvent(AutonomicManager target, int appId) {
		super(target);
		
		this.appId = appId;
	}
	
	public int getAppId() {
		return appId;
	}

}
