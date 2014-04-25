package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.management.events.HostStatusEvent;
import edu.uwo.csd.dcsim.management.events.MigrationCompleteEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.VmHostMapManager;

public class VmHostMapPolicy extends Policy {

	public VmHostMapPolicy() {
		addRequiredCapability(VmHostMapManager.class);
		addRequiredCapability(HostPoolManager.class);
	}
	
	public void execute(HostStatusEvent event) {
		VmHostMapManager vmHostMap = manager.getCapability(VmHostMapManager.class);
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		HostStatus status = event.getHostStatus();
		HostData host = hostPool.getHost(status.getId());
		for (VmStatus vm : status.getVms()) {
			vmHostMap.addMapping(vm, host, event.getTime());
		}
	}
	
	public void execute(MigrationCompleteEvent event) {
		manager.getCapability(VmHostMapManager.class).updateMapping(event.getVmId(), event.getTargetHostId(), event.getTime());
	}
	
	@Override
	public void onInstall() {
		// Auto-generated method stub
	}

	@Override
	public void onManagerStart() {
		// Auto-generated method stub
	}

	@Override
	public void onManagerStop() {
		// Auto-generated method stub
	}

}
