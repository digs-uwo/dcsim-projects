package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class AcceptVmEvent extends MessageEvent {

	private VmStatus vm;
	private Host host;
	private AutonomicManager hostManager;
	private HostStatus hostStatus;
	
	public AcceptVmEvent(SimulationEventListener target, VmStatus vm, Host host, AutonomicManager hostManager, HostStatus hostStatus) {
		super(target);
		
		this.vm = vm;
		this.host = host;
		this.hostManager = hostManager;
		this.hostStatus = hostStatus;
	}
	
	public VmStatus getVm() {
		return vm;
	}
	
	public Host getHost() {
		return host;
	}
	
	public AutonomicManager getHostManager() {
		return hostManager;
	}
	
	public HostStatus getHostStatus() {
		return hostStatus;
	}

}
