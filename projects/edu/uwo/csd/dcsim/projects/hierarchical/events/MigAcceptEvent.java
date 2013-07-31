package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class MigAcceptEvent extends MessageEvent {

	private VmStatus vm;
	private Host targetHost;
	
	public MigAcceptEvent(AutonomicManager target, VmStatus vm, Host targetHost) {
		super(target);
		
		this.vm = vm;
		this.targetHost = targetHost;
	}
	
	public VmStatus getVm() {
		return vm;
	}
	
	public Host getTargetHost() {
		return targetHost;
	}

}
