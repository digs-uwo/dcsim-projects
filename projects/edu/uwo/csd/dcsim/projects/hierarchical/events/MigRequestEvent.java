package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class MigRequestEvent extends MessageEvent {

	private VmStatus vm;
	private AutonomicManager origin;
	
	public MigRequestEvent(AutonomicManager target, VmStatus vm, AutonomicManager origin) {
		super(target);
		
		this.vm = vm;
		this.origin = origin;
	}
	
	public VmStatus getVm() {
		return vm;
	}
	
	public AutonomicManager getOrigin() {
		return origin;
	}

}
