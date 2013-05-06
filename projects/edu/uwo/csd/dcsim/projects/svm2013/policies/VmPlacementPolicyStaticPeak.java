package edu.uwo.csd.dcsim.projects.svm2013.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent.PowerState;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.management.events.InstantiateVmEvent;
import edu.uwo.csd.dcsim.management.events.ShutdownVmEvent;
import edu.uwo.csd.dcsim.management.events.VmPlacementEvent;
import edu.uwo.csd.dcsim.vm.VMAllocation;
import edu.uwo.csd.dcsim.vm.VMAllocationRequest;

/**
 * Implements a greedy algorithm for VM Placement. VMs are placed in the first 
 * host that can fit the VM without over-committing resources.
 * 
 * Hosts are sorted in decreasing order by <power efficiency, volume of allocated resources>, 
 * where *volume of allocated resources* is the ratio of total allocated resources to resource
 * capacity of the host: (cpuAlloc * memAlloc * bwAlloc) / (cpuTotal * memory * bandwidth).
 * 
 * @author Gaston Keller
 *
 */
public class VmPlacementPolicyStaticPeak extends Policy {

	public VmPlacementPolicyStaticPeak() {
		addRequiredCapability(HostPoolManager.class);
	}
	
	/**
	 * Calculates the total allocated CPU in a host, if the CPU were not to be 
	 * over-subscribed. Calculation includes CPU allocation to the privileged 
	 * domain.
	 */
	public double calculateTotalAllocatedCpu(HostData host) {
		ArrayList<VMAllocation> vms = new ArrayList<VMAllocation>(host.getHost().getVMAllocations());
		vms.add(host.getHost().getPrivDomainAllocation());
		
		double cpuAlloc = 0;
		for (VMAllocation vm : vms) {
			// If the VMAllocation has an associated VM, record its resource allocation.
			if (vm.getVm() != null)
				cpuAlloc += vm.getCpu();
		}
		
		return cpuAlloc;
	}
	
	public void execute(VmPlacementEvent event) {
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		Collection<HostData> hosts = hostPool.getHosts();
		
		//reset the sandbox host status to the current host status
		for (HostData host : hosts) {
			host.resetSandboxStatusToCurrent();
		}
		
		// Create target hosts list.
		ArrayList<HostData> targets = new ArrayList<HostData>(hosts);
		
		// Sort target hosts in decreasing order by <power efficiency, volume of allocated resources>,
		// where *volume of allocated resources* is the ratio of total allocated resources to resource 
		// capacity of the host: (cpuAlloc * memAlloc * bwAlloc) / (cpuTotal * memory * bandwidth).
		Collections.sort(targets, HostDataComparator.getComparator(HostDataComparator.EFFICIENCY, HostDataComparator.VOLUME_ALLOC));
		Collections.reverse(targets);
		
		for (VMAllocationRequest vmAllocationRequest : event.getVMAllocationRequests()) {
			HostData allocatedHost = null;
			for (HostData target : targets) {
				Resources reqResources = new Resources();
				reqResources.setCpu(vmAllocationRequest.getCpu());
				reqResources.setMemory(vmAllocationRequest.getMemory());
				reqResources.setBandwidth(vmAllocationRequest.getBandwidth());
				reqResources.setStorage(vmAllocationRequest.getStorage());
				
				// Check that target host is capable and has enough capacity left to host the VM, 
				// and also that it will not exceed the target utilization.
				if (HostData.canHost(vmAllocationRequest.getVMDescription().getCores(), 
						vmAllocationRequest.getVMDescription().getCoreCapacity(), 
						reqResources,
						target.getSandboxStatus(),
						target.getHostDescription()) &&	
					(this.calculateTotalAllocatedCpu(target) + vmAllocationRequest.getCpu()) <= target.getHostDescription().getResourceCapacity().getCpu()) {
					
					allocatedHost = target;
					
					//add a dummy placeholder VM to keep track of placed VM resource requirements
					target.getSandboxStatus().instantiateVm(
							new VmStatus(vmAllocationRequest.getVMDescription().getCores(),
							vmAllocationRequest.getVMDescription().getCoreCapacity(),
							reqResources));
					
					//invalidate this host status, as we know it to be incorrect until the next status update arrives
					target.invalidateStatus(simulation.getSimulationTime());
					
					break;
				 }
			}
			
			if (allocatedHost != null) {
				sendVM(vmAllocationRequest, allocatedHost);
			} else {
				event.addFailedRequest(vmAllocationRequest); //add a failed request to the event for any event callback listeners to check
			}
		}
	}
	
	private long sendVM(VMAllocationRequest vmAllocationRequest, HostData host) {
		//if the host is not ON or POWERING_ON, then send an event to power on the host
		if (host.getCurrentStatus().getState() != Host.HostState.ON && host.getCurrentStatus().getState() != Host.HostState.POWERING_ON) {
			simulation.sendEvent(new PowerStateEvent(host.getHost(), PowerState.POWER_ON));
			
		}

		//send event to host to instantiate VM
		return simulation.sendEvent(new InstantiateVmEvent(host.getHostManager(), vmAllocationRequest));
	}
	
	public void execute(ShutdownVmEvent event) {
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		AutonomicManager hostManager = hostPool.getHost(event.getHostId()).getHostManager();
		
		//mark host status as invalid
		hostPool.getHost(event.getHostId()).invalidateStatus(simulation.getSimulationTime());
		
		//prevent the original event from logging, since we are creating a new event to forward to the host
		event.setLog(false);
		
		simulation.sendEvent(new ShutdownVmEvent(hostManager, event.getHostId(), event.getVmId()));
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
