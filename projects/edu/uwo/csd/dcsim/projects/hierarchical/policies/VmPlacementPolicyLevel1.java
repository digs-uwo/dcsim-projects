package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collection;

import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent.PowerState;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.management.events.*;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.RackManager;
import edu.uwo.csd.dcsim.projects.hierarchical.events.VmPlacementRejectEvent;
import edu.uwo.csd.dcsim.vm.VMAllocationRequest;

/**
 * This policy implements the VM Placement process using a greedy algorithm. 
 * VMs are placed in the first non-stressed host with enough spare capacity 
 * to take the VM without exceeding the *targetUtilization* threshold.
 * 
 * Hosts are classified as Stressed, Partially-Utilized, Underutilized or 
 * Empty based on the hosts' average CPU utilization over the last window of 
 * time.
 * 
 * @author Gaston Keller
 *
 */
public abstract class VmPlacementPolicyLevel1 extends Policy {

	protected AutonomicManager target;
	
	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	
	/**
	 * Creates an instance of VmPlacementPolicyLevel1.
	 */
	public VmPlacementPolicyLevel1(AutonomicManager target, double lowerThreshold, double upperThreshold, double targetUtilization) {
		addRequiredCapability(HostPoolManager.class);
		
		this.target = target;
		
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}
	
	/**
	 * Sorts the target hosts (Partially-Utilized, Underutilized and Empty) in 
	 * the order in which they are to be considered for VM Placement.
	 */
	protected abstract ArrayList<HostData> orderTargetHosts(ArrayList<HostData> partiallyUtilized, 
			ArrayList<HostData> underUtilized, 
			ArrayList<HostData> empty);
	
	/**
	 * This event can only come from the ClusterManager.
	 * 
	 * Places the given VM in the first non-stressed Host with enough spare capacity 
	 * to take the VM without exceeding the *targetUtilization* threshold.
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
		
		// The event contains a single placement request.
		VMAllocationRequest request = event.getVMAllocationRequests().get(0);
		
		HostData targetHost = null;
		for (HostData target : targets) {
			
			Resources reqResources = new Resources();
			reqResources.setCpu(request.getCpu());
			reqResources.setMemory(request.getMemory());
			reqResources.setBandwidth(request.getBandwidth());
			reqResources.setStorage(request.getStorage());
			
			// Check that target Host is capable and has enough capacity left to host the VM, 
			// and also that it will not exceed the target utilization.
			if (HostData.canHost(request.getVMDescription().getCores(), 
					request.getVMDescription().getCoreCapacity(), 
					reqResources,
					target.getSandboxStatus(),
					target.getHostDescription()) &&	
			 	(target.getSandboxStatus().getResourcesInUse().getCpu() + request.getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
				
				targetHost = target;
				
				// Add a dummy placeholder VM to keep track of placed VM resource requirements.
				target.getSandboxStatus().instantiateVm(new VmStatus(request.getVMDescription().getCores(),	request.getVMDescription().getCoreCapacity(), reqResources));
				
				// Invalidate this host status, as we know it to be incorrect until the next status update arrives.
				target.invalidateStatus(simulation.getSimulationTime());
				
				break;
			}
		}
		
		if (null != targetHost)
			// Found target. Send instantiation request.
			this.sendVm(request, targetHost);
		// Could not find suitable target Host in the Rack.
		else {
			int rackId = manager.getCapability(RackManager.class).getRack().getId();
			
			// Contact ClusterManager. Reject placement request.
			simulation.sendEvent(new VmPlacementRejectEvent(target, request, rackId));
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
	
	private long sendVm(VMAllocationRequest vmAllocationRequest, HostData host) {
		// If the host is not ON or POWERING_ON, then send an event to power it on.
		if (host.getCurrentStatus().getState() != Host.HostState.ON && host.getCurrentStatus().getState() != Host.HostState.POWERING_ON) {
			simulation.sendEvent(new PowerStateEvent(host.getHost(), PowerState.POWER_ON));
		}

		// Send event to host to instantiate VM.
		return simulation.sendEvent(new InstantiateVmEvent(host.getHostManager(), vmAllocationRequest));
	}
	
	/**
	 * Classifies hosts as Partially-Utilized, Under-Utilized or Empty based 
	 * on the Hosts' average CPU utilization over the last window of time. The 
	 * method ignores (or discards) Stressed Hosts.
	 */
	protected void classifyHosts(ArrayList<HostData> partiallyUtilized, 
			ArrayList<HostData> underUtilized, 
			ArrayList<HostData> empty, 
			Collection<HostData> hosts) {
		
		for (HostData host : hosts) {
			
			// Filter out Hosts with a currently invalid status.
			if (host.isStatusValid()) {
				
				double avgCpuUtilization = this.calculateHostAvgCpuUtilization(host);
				
				// Classify hosts.
				if (host.getCurrentStatus().getVms().size() == 0) {
					empty.add(host);
				} else if (avgCpuUtilization < lowerThreshold) {
					underUtilized.add(host);
				} else if (avgCpuUtilization < upperThreshold) {
					partiallyUtilized.add(host);
				}
			}
		}
	}
	
	/**
	 * Calculates Host's average CPU utilization over the last window of time.
	 * 
	 * @return		value in range [0,1] (i.e., percentage)
	 */
	protected double calculateHostAvgCpuUtilization(HostData host) {
		double avgCpuInUse = 0;
		int count = 0;
		for (HostStatus status : host.getHistory()) {
			// Only consider times when the host is powered ON.
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
		
		return Utility.roundDouble(avgCpuInUse / host.getHostDescription().getResourceCapacity().getCpu());
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
