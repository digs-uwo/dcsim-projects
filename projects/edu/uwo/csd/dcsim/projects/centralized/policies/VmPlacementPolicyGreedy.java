package edu.uwo.csd.dcsim.projects.centralized.policies;

import java.util.ArrayList;
import java.util.Collection;

import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent.PowerState;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.management.events.InstantiateVmEvent;
import edu.uwo.csd.dcsim.management.events.ShutdownVmEvent;
import edu.uwo.csd.dcsim.management.events.VmPlacementEvent;
import edu.uwo.csd.dcsim.vm.VMAllocationRequest;

/**
 * Implements a greedy algorithm for VM Placement. VMs are placed in the first 
 * non-stressed host with enough spare capacity to take the VM without 
 * exceeding the _targetUtilization_ threshold.
 * 
 * Hosts are classified as Stressed, Partially-Utilized, Underutilized or 
 * Empty based on the hosts' average CPU utilization over the last window of 
 * time.
 * 
 * @author Gaston Keller
 *
 */
public abstract class VmPlacementPolicyGreedy extends Policy {

	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	
	/**
	 * Creates an instance of VmPlacementPolicyGreedy.
	 */
	public VmPlacementPolicyGreedy(double lowerThreshold, double upperThreshold, double targetUtilization) {
		addRequiredCapability(HostPoolManager.class);
		
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}
	
	/**
	 * Sorts the target hosts (Partially-utilized, Underutilized and Empty) in 
	 * the order in which they are to be considered for VM Placement.
	 */
	protected abstract ArrayList<HostData> orderTargetHosts(ArrayList<HostData> partiallyUtilized, 
			ArrayList<HostData> underUtilized, 
			ArrayList<HostData> empty);
	
	/**
	 * Classifies hosts as Partially-Utilized, Underutilized or Empty based on 
	 * the hosts' average CPU utilization over the last window of time.
	 */
	protected void classifyHosts(ArrayList<HostData> partiallyUtilized, 
			ArrayList<HostData> underUtilized, 
			ArrayList<HostData> empty, 
			Collection<HostData> hosts) {
		
		for (HostData host : hosts) {
			
			// Filter out hosts with a currently invalid status.
			if (host.isStatusValid()) {
				
				// Calculate host's avg CPU utilization over the last window of time.
				double avgCpuInUse = 0;
				int count = 0;
				for (HostStatus status : host.getHistory()) {
					// Only consider times when the host is powered on.
					if (status.getState() == Host.HostState.ON) {
						avgCpuInUse += status.getResourcesInUse().getCpu();
						++count;
					}
					else
						break;
				}
				if (count != 0) {
					avgCpuInUse = avgCpuInUse / count;
				}
				
				double avgCpuUtilization = Utility.roundDouble(avgCpuInUse / host.getHostDescription().getResourceCapacity().getCpu());
				
				// Classify hosts.
				if (host.getCurrentStatus().getVms().size() == 0) {
					empty.add(host);
				} else if (avgCpuUtilization < lowerThreshold) {
					underUtilized.add(host);
				} else if (avgCpuUtilization <= upperThreshold) {
					partiallyUtilized.add(host);
				}
			}
		}
	}
	
	public void execute(ShutdownVmEvent event) {
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		AutonomicManager hostManager = hostPool.getHost(event.getHostId()).getHostManager();
		
		// Mark host status as invalid.
		hostPool.getHost(event.getHostId()).invalidateStatus(simulation.getSimulationTime());
		
		//prevent the original event from logging, since we are creating a new event to forward to the host
		event.setLog(false);
		
		simulation.sendEvent(new ShutdownVmEvent(hostManager, event.getHostId(), event.getVmId()));
	}
	
	/**
	 * Places a set of VMs, one by one, in the first non-stressed host with 
	 * enough spare capacity to take a VM without exceeding the 
	 * _targetUtilization_ threshold.
	 */
	public void execute(VmPlacementEvent event) {
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		Collection<HostData> hosts = hostPool.getHosts();
		
		// Reset the sandbox host status to the current host status.
		for (HostData host : hosts) {
			host.resetSandboxStatusToCurrent();
		}
		
		// Categorize hosts.
		ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
		ArrayList<HostData> underUtilized = new ArrayList<HostData>();
		ArrayList<HostData> empty = new ArrayList<HostData>();
		
		this.classifyHosts(partiallyUtilized, underUtilized, empty, hosts);
		
		// Create target hosts list.
		ArrayList<HostData> targets = this.orderTargetHosts(partiallyUtilized, underUtilized, empty);
		
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
				 	(target.getSandboxStatus().getResourcesInUse().getCpu() + vmAllocationRequest.getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
					
					allocatedHost = target;
					
					// Add a dummy placeholder VM to keep track of placed VM resource requirements.
					target.getSandboxStatus().instantiateVm(
							new VmStatus(vmAllocationRequest.getVMDescription().getCores(),
									vmAllocationRequest.getVMDescription().getCoreCapacity(),
									reqResources));
					
					// Invalidate this host status, as we know it to be incorrect until the next status update arrives.
					target.invalidateStatus(simulation.getSimulationTime());
					
					break;
				}
			}
			
			if (allocatedHost != null)
				sendVm(vmAllocationRequest, allocatedHost);
			else
				event.addFailedRequest(vmAllocationRequest); // Add a failed request to the event for any event callback listeners to check.
		}
	}
	
	private long sendVm(VMAllocationRequest vmAllocationRequest, HostData host) {
		// If the host is not ON or POWERING_ON, then send an event to power it on.
		if (host.getCurrentStatus().getState() != Host.HostState.ON && host.getCurrentStatus().getState() != Host.HostState.POWERING_ON) {
			simulation.sendEvent(new PowerStateEvent(host.getHost(), PowerState.POWER_ON));
		}

		// Send event to host to instantiate VM.
		return simulation.sendEvent(new InstantiateVmEvent(host.getHostManager(), vmAllocationRequest));
	}
	
	/* (non-Javadoc)
	 * @see edu.uwo.csd.dcsim.management.Policy#onInstall()
	 */
	@Override
	public void onInstall() {
		// TODO Auto-generated method stub
	}

	/* (non-Javadoc)
	 * @see edu.uwo.csd.dcsim.management.Policy#onManagerStart()
	 */
	@Override
	public void onManagerStart() {
		// TODO Auto-generated method stub
	}

	/* (non-Javadoc)
	 * @see edu.uwo.csd.dcsim.management.Policy#onManagerStop()
	 */
	@Override
	public void onManagerStop() {
		// TODO Auto-generated method stub
	}

}
