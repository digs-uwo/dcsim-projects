package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class SurrogateAppDataEvent extends MessageEvent {

	private int appId;
	private int taskId;
	private VmStatus vm;
	private AutonomicManager origin;
	
	public SurrogateAppDataEvent(AutonomicManager target, int appId, int taskId, VmStatus vm, AutonomicManager origin) {
		super(target);
		
		this.appId = appId;
		this.taskId = taskId;
		this.vm = vm;
		this.origin = origin;
	}
	
	public int getAppId() {
		return appId;
	}
	
	public int getTaskId() {
		return taskId;
	}
	
	public VmStatus getVm() {
		return vm;
	}
	
	public AutonomicManager getOrigin() {
		return origin;
	}

}
