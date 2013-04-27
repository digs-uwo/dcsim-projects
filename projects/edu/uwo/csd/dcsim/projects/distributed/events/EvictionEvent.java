package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.VmStatus;

public class EvictionEvent extends Event {

	VmStatus vmStatus;
	
	public EvictionEvent(SimulationEventListener target, VmStatus vm) {
		super(target);
		
		this.vmStatus = vm;
	}
	
	public VmStatus getVm() {
		return vmStatus;
	}

}
