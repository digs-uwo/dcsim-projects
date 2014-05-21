package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.management.events.HostStatusEvent;
import edu.uwo.csd.dcsim.management.events.MigrationCompleteEvent;
import edu.uwo.csd.dcsim.management.events.VmInstantiationCompleteEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.TaskData;
import edu.uwo.csd.dcsim.projects.hierarchical.VmData;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.AppPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.VmPoolManager;

public class VmPoolPolicy extends Policy {

	public VmPoolPolicy() {
		addRequiredCapability(AppPoolManager.class);
		addRequiredCapability(HostPoolManager.class);
		addRequiredCapability(VmPoolManager.class);
	}
	
	public void execute(HostStatusEvent event) {
		VmPoolManager vmPool = manager.getCapability(VmPoolManager.class);
		for (VmStatus status : event.getHostStatus().getVms()) {
			vmPool.getVm(status.getId()).setCurrentStatus(status);
		}
	}
	
	public void execute(MigrationCompleteEvent event) {
		VmPoolManager vmPool = manager.getCapability(VmPoolManager.class);
		HostData targetHost = manager.getCapability(HostPoolManager.class).getHost(event.getTargetHostId());
		if (null != targetHost) {		// Target Host is local.
			vmPool.getVm(event.getVmId()).updateHost(targetHost);
		}
		else {		// Target Host is remote. Remove VM from pool.
			vmPool.removeVm(event.getVmId());
		}
	}
	
	public void execute(VmInstantiationCompleteEvent event) {
		TaskData task = manager.getCapability(AppPoolManager.class).getApplication(event.getApplicationId()).getTask(event.getTaskId());
		task.setHostingVm(event.getVmId());
		manager.getCapability(VmPoolManager.class).addVm(new VmData(event.getVmId(), task, manager.getCapability(HostPoolManager.class).getHost(event.getHostId())));
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
