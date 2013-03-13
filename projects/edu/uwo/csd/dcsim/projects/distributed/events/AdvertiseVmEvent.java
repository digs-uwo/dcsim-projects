package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class AdvertiseVmEvent extends MessageEvent {

	private VmStatus vm;
	private AutonomicManager hostManager;
	
	public AdvertiseVmEvent(SimulationEventListener target, VmStatus vm, AutonomicManager hostManager) {
		super(target);
		
		this.vm = vm;
		this.hostManager = hostManager;
	}
	
	public VmStatus getVm() {
		return vm;
	}
	
	public AutonomicManager getHostManager() {
		return hostManager;
	}

}
