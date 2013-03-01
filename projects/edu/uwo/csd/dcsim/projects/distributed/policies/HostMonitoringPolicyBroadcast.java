package edu.uwo.csd.dcsim.projects.distributed.policies;

import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.capabilities.*;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.*;
import edu.uwo.csd.dcsim.projects.distributed.events.*;

public class HostMonitoringPolicyBroadcast extends Policy {

	private double lower;
	private double upper;
	private double target;
	
	public HostMonitoringPolicyBroadcast(double lower, double upper, double target) {
		addRequiredCapability(HostManager.class);
		
		this.lower = lower;
		this.upper = upper;
		this.target = target;
	}
	
	//executes on a regular interval to check host status, and take possible action
	public void execute() {
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		HostStatus hostStatus = new HostStatus(hostManager.getHost(), simulation.getSimulationTime());
		hostManager.addHistoryStatus(hostStatus, 5);
		
		if (isStressed(hostManager, hostStatus)) {
			//host is stressed, evict a VM
		} else if (isUnderUtilized(hostManager, hostStatus)) {
			//host is underutilized, decide if should switch to eviction, if so, evict a VM
		}
		
	}
	
	private boolean isStressed(HostManagerBroadcast hostManager, HostStatus hostStatus) {
		//TODO change to use average over history
		if (hostManager.getHost().getResourceManager().getCpuInUse() / hostManager.getHost().getTotalCpu() > upper) {
			return true;
		}
		return false;
	}
	
	private boolean isUnderUtilized(HostManagerBroadcast hostManager, HostStatus hostStatus) {
		//TODO change to use average over history
		if (hostManager.getHost().getResourceManager().getCpuInUse() / hostManager.getHost().getTotalCpu() < lower) {
			return true;
		}
		return false;
	}
	
	public void execute(AdvertiseVmEvent event) {
		//received a VM advertisement
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		HostStatus hostStatus = new HostStatus(hostManager.getHost(), simulation.getSimulationTime());
		
		if (!isStressed(hostManager, hostStatus) && !hostManager.isEvicting()) {
			//if we are both not stressed and not current evicting our VMs, we can take this VM
			
		}
		
	}
	
	public void execute(AcceptVmEvent event) {
		//a Host has accepted your advertised VM
		
		//migrate VM
	}
	
	public void execute(HostShuttingDownEvent event) {
		//store in list of powered off hosts
	}
	
	@Override
	public void onInstall() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStart() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStop() {
		// TODO Auto-generated method stub
		
	}

}
