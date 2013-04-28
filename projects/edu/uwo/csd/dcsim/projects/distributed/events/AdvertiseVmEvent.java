package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class AdvertiseVmEvent extends MessageEvent {

	public enum AdvertiseReason {STRESS, SHUTDOWN, PLACEMENT;}
	
	private VmStatus vm;
	private AutonomicManager hostManager;
	private AdvertiseReason reason;
	
	public AdvertiseVmEvent(SimulationEventListener target, VmStatus vm, AutonomicManager hostManager, AdvertiseReason reason) {
		super(target);
		
		this.vm = vm;
		this.hostManager = hostManager;
		this.reason = reason;
	}
	
	public VmStatus getVm() {
		return vm;
	}
	
	public AutonomicManager getHostManager() {
		return hostManager;
	}
	
	public AdvertiseReason getReason() {
		return reason;
	}

}
