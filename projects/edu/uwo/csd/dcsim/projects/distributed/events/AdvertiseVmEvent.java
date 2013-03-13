package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class AdvertiseVmEvent extends MessageEvent {

	VmStatus vm;
	
	public AdvertiseVmEvent(SimulationEventListener target, VmStatus vm) {
		super(target);
		
		this.vm = vm;
	}
	
	public VmStatus getVm() {
		return vm;
	}

}
