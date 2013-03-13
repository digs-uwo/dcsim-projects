package edu.uwo.csd.dcsim.projects.distributed.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.capabilities.*;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.*;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.HostManagerBroadcast.ManagementState;
import edu.uwo.csd.dcsim.projects.distributed.events.*;

public class HostMonitoringPolicyBroadcast extends Policy {

	private static final int EVICTION_TIMEOUT = 2; //the number of rounds to wait to evict a VM
	
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
		
		//if evicting, wait until eviction counter runs out
		if (hostManager.isEvicting()) {
			//decrement evicting counter
			hostManager.setEvicting(hostManager.getEvicting() - 1);
			
			//resend advertise message
			simulation.sendEvent(new AdvertiseVmEvent(hostManager.getBroadcastingGroup(), hostManager.getEvictingVm()));
			
		} else if (hostManager.getManagementState() == ManagementState.SHUTTING_DOWN) {
			//if shutting down
			
			//check for failed eviction, if failed, cancel shut down
			
			//if evictions complete, shut down
			
			//send shutdown message
			//simulation.sendEvent(new HostShuttingDownEvent(hostManager.getBroadcastingGroup(), hostManager.getHost()));
			
		} else if (isStressed(hostManager, hostStatus)) {
			//host is stressed, evict a VM
			
			//check for failed eviction 
				//if failed, either try another VM or boot new host and retry eviction
			
			//sort VMs
			ArrayList<VmStatus> vmList = orderVmsStressed(hostStatus.getVms(), hostManager.getHost());
			
			//evict first VM in list
			if (!vmList.isEmpty()) {
				evict(hostManager, vmList.get(0));	
			}
			
		} else if (isUnderUtilized(hostManager, hostStatus)) {
			//host is underutilized, decide if should switch to eviction, if so, evict a VM
			
			//for now, always switch. TODO add better logic, perhaps a probability, or base it on the number of received other eviction messages
			//hostManager.setManagementState(ManagementState.SHUTTING_DOWN); 

		} else {
			//check for received VM advertisements
			for (AdvertiseVmEvent ad : hostManager.getVmAdvertisements()) {
				//check if we are capable of hosting this VM
				
				//if yes, then send an accept message
			}
		}
		
		//clear VM advertisements
		hostManager.getVmAdvertisements().clear();
	}

	private ArrayList<VmStatus> orderVmsStressed(ArrayList<VmStatus> vms, Host host) {
		ArrayList<VmStatus> sorted = new ArrayList<VmStatus>();
		
		// Remove VMs with less CPU load than the CPU load by which the
		// host is stressed.
		double cpuExcess = host.getResourceManager().getCpuInUse() - host.getTotalCpu() * upper;
		for (VmStatus vm : vms)
			if (vm.getResourcesInUse().getCpu() >= cpuExcess)
				sorted.add(vm);
		
		if (!sorted.isEmpty())
			// Sort VMs in increasing order by CPU load.
			Collections.sort(sorted, VmStatusComparator.getComparator(VmStatusComparator.CPU_IN_USE));
		else {
			// Add original list of VMs and sort them in decreasing order by 
			// CPU load, so as to avoid trying to migrate the smallest VMs 
			// first (which would not help resolve the stress situation).
			sorted.addAll(vms);
			Collections.sort(sorted, VmStatusComparator.getComparator(VmStatusComparator.CPU_IN_USE));
			Collections.reverse(sorted);
		}
		
		return sorted;
	}
	
	private ArrayList<VmStatus> orderVmsUnderUtilized(ArrayList<VmStatus> vms) {
		ArrayList<VmStatus> sources = new ArrayList<VmStatus>(vms);
		
		// Sort VMs in decreasing order by <overall capacity, CPU load>.
		// (Note: since CPU can be oversubscribed, but memory can't, memory 
		// takes priority over CPU when comparing VMs by _size_ (capacity).)
		Collections.sort(sources, VmStatusComparator.getComparator(
				VmStatusComparator.MEMORY, 
				VmStatusComparator.CPU_CORES, 
				VmStatusComparator.CORE_CAP, 
				VmStatusComparator.CPU_IN_USE));
		Collections.reverse(sources);
		
		return sources;
	}
	
	private void evict(HostManagerBroadcast hostManager, VmStatus vm) {
		//setup eviction counter/data
		hostManager.setEvicting(EVICTION_TIMEOUT);
		hostManager.setEvictingVm(vm);
				
		//send advertise message
		simulation.sendEvent(new AdvertiseVmEvent(hostManager.getBroadcastingGroup(), vm));		
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
		
		hostManager.getVmAdvertisements().add(event);
	}
	
	public void execute(AcceptVmEvent event) {
		//a Host has accepted your advertised VM
		
		//migrate VM
		
		//clear eviction counter/data
	}
	
	public void execute(HostShuttingDownEvent event) {
		//store in list of powered off hosts
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		hostManager.getPoweredOffHosts().add(event.getHost());
	}
	
	public void execute(HostPowerOnEvent event) {
		//remove from list of powered off hosts
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		hostManager.getPoweredOffHosts().remove(event.getHost());
	}
	
	@Override
	public void onInstall() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStart() {
		//send power on message
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		simulation.sendEvent(new HostPowerOnEvent(hostManager.getBroadcastingGroup(), hostManager.getHost()));
	}

	@Override
	public void onManagerStop() {
		
	}

}
