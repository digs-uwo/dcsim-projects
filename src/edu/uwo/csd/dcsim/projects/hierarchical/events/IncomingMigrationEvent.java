package edu.uwo.csd.dcsim.projects.hierarchical.events;

import java.util.Collection;
import java.util.Map;

import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.events.MessageEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.AppData;
import edu.uwo.csd.dcsim.projects.hierarchical.VmData;

public class IncomingMigrationEvent extends MessageEvent {

	private AppData application;
	private Collection<VmData> vms;
	private Map<Integer, Host> vmHostMap;
	
	public IncomingMigrationEvent(AutonomicManager target, AppData application, Collection<VmData> vms, Map<Integer, Host> vmHostMap) {
		super(target);
		
		this.application = application;
		this.vms = vms;
		this.vmHostMap = vmHostMap;
	}
	
	public AppData getApplication() {
		return application;
	}
	
	public Collection<VmData> getVms() {
		return vms;
	}
	
	public Map<Integer, Host> getTargetHosts() {
		return vmHostMap;
	}

}
