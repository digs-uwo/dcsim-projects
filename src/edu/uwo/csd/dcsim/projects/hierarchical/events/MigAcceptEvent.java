package edu.uwo.csd.dcsim.projects.hierarchical.events;

import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class MigAcceptEvent extends MessageEvent {

	private VmStatus vm;
	private Host targetHost;
	private AutonomicManager origin;			// Manager accepting the migration.
	
	@Deprecated
	public MigAcceptEvent(AutonomicManager target, VmStatus vm, Host targetHost) {
		super(target);
		
		this.vm = vm;
		this.targetHost = targetHost;
	}
	
	public MigAcceptEvent(AutonomicManager target, VmStatus vm, Host targetHost, AutonomicManager origin) {
		super(target);
		
		this.vm = vm;
		this.targetHost = targetHost;
		this.origin = origin;
	}
	
	public VmStatus getVm() {
		return vm;
	}
	
	public Host getTargetHost() {
		return targetHost;
	}
	
	public AutonomicManager getOrigin() {
		return origin;
	}

}
