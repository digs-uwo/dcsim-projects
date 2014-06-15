package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class SurrogateAppDataEvent extends MessageEvent {

	private int appId;
	private VmStatus vm;
	private AutonomicManager origin;
	
	public SurrogateAppDataEvent(AutonomicManager target, int appId, VmStatus vm, AutonomicManager origin) {
		super(target);
		
		this.appId = appId;
		this.vm = vm;
		this.origin = origin;
	}
	
	public int getAppId() {
		return appId;
	}
	
	public VmStatus getVm() {
		return vm;
	}
	
	public AutonomicManager getOrigin() {
		return origin;
	}

}
