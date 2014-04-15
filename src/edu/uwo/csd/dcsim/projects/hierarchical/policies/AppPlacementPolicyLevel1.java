package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.HostDataComparator;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.action.InstantiateVmAction;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.management.events.ShutdownVmEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.ConstrainedAppAllocationRequest;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.RackManager;
import edu.uwo.csd.dcsim.projects.hierarchical.events.PlacementRejectEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.PlacementRequestEvent;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;

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
public class AppPlacementPolicyLevel1 extends Policy {

	protected AutonomicManager target;
	
	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	
	/**
	 * Creates an instance of AppPlacementPolicyLevel1.
	 */
	public AppPlacementPolicyLevel1(AutonomicManager target, double lowerThreshold, double upperThreshold, double targetUtilization) {
		addRequiredCapability(HostPoolManager.class);
		
		this.target = target;
		
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}
	
	/**
	 * Note: This event can only come from the ClusterManager.
	 */
	public void execute(PlacementRequestEvent event) {
		ArrayList<InstantiateVmAction> placements = this.processRequest(event);
		if (null != placements) {
			for (InstantiateVmAction action : placements) {
				// Invalidate target Host's status, as we know it to be incorrect until the next status update arrives.
				action.getTarget().invalidateStatus(simulation.getSimulationTime());
				action.execute(simulation, this);
			}
		}
		else {	// Contact ClusterManager - reject placement request.
			int rackId = manager.getCapability(RackManager.class).getRack().getId();
			simulation.sendEvent(new PlacementRejectEvent(target, event.getRequest(), rackId));
		}
	}
	
	protected ArrayList<InstantiateVmAction> processRequest(PlacementRequestEvent event) {
		ArrayList<InstantiateVmAction> actions = new ArrayList<InstantiateVmAction>();
		
		ConstrainedAppAllocationRequest request = event.getRequest();
		
		Collection<HostData> hosts = manager.getCapability(HostPoolManager.class).getHosts();
		
		// Reset the sandbox host status to the current host status.
		for (HostData host : hosts) {
			host.resetSandboxStatusToCurrent();
		}
		
		// Categorize hosts.
		ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
		ArrayList<HostData> underUtilized = new ArrayList<HostData>();
		ArrayList<HostData> empty = new ArrayList<HostData>();
		this.classifyHosts(hosts, partiallyUtilized, underUtilized, empty);
		
		// Create target hosts list.
		ArrayList<HostData> targets = this.orderTargetHosts(partiallyUtilized, underUtilized, empty);
		
		for (ArrayList<VmAllocationRequest> affinitySet : request.getAffinityVms()) {
			ArrayList<InstantiateVmAction> placements = this.placeVmsTogether(affinitySet, targets, event);
			if (null != placements)
				actions.addAll(placements);
			else
				return null;
		}
		
		for (ArrayList<VmAllocationRequest> antiAffinitySet : request.getAntiAffinityVms()) {
			ArrayList<InstantiateVmAction> placements = this.placeVmsApart(antiAffinitySet, targets, event);
			if (null != placements)
				actions.addAll(placements);
			else
				return null;
		}
		
		for (VmAllocationRequest req : request.getIndependentVms()) {
			InstantiateVmAction placement = this.placeVmWherever(req, targets, event);
			if (null != placement)
				actions.add(placement);
			else
				return null;
		}
		
		// If we don't have a Placement action for each allocation request, then there's an implementation error somewhere.
		assert request.getAllVmAllocationRequests().size() == actions.size();
		
		return actions;
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
	 * Classifies hosts as Partially-Utilized, Under-Utilized or Empty based 
	 * on the Hosts' average CPU utilization over the last window of time. The 
	 * method ignores (or discards) Stressed Hosts.
	 */
	protected void classifyHosts(Collection<HostData> hosts, ArrayList<HostData> partiallyUtilized, ArrayList<HostData> underUtilized, ArrayList<HostData> empty) {
		
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
	
	/**
	 * Sorts the target hosts (Partially-Utilized, Underutilized and Empty) in
	 * the order in which they are to be considered for VM Placement.
	 */
	protected ArrayList<HostData> orderTargetHosts(ArrayList<HostData> partiallyUtilized, ArrayList<HostData> underUtilized, ArrayList<HostData> empty) {
		ArrayList<HostData> targets = new ArrayList<HostData>();
		
		// Sort Partially-utilized in increasing order by <CPU utilization, power efficiency>.
		Collections.sort(partiallyUtilized, HostDataComparator.getComparator(HostDataComparator.CPU_UTIL, HostDataComparator.EFFICIENCY));
		
		// Sort Underutilized hosts in decreasing order by <CPU utilization, power efficiency>.
		Collections.sort(underUtilized, HostDataComparator.getComparator(HostDataComparator.CPU_UTIL, HostDataComparator.EFFICIENCY));
		Collections.reverse(underUtilized);
		
		// Sort Empty hosts in decreasing order by <power efficiency, power state>.
		Collections.sort(empty, HostDataComparator.getComparator(HostDataComparator.EFFICIENCY, HostDataComparator.PWR_STATE));
		Collections.reverse(empty);
		
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		targets.addAll(empty);
		
		return targets;
	}
	
	protected InstantiateVmAction placeVmWherever(VmAllocationRequest request,	Collection<HostData> targets, PlacementRequestEvent event) {
		
		Resources reqResources = new Resources();
		reqResources.setCpu(request.getCpu());
		reqResources.setMemory(request.getMemory());
		reqResources.setBandwidth(request.getBandwidth());
		reqResources.setStorage(request.getStorage());
		
		for (HostData target : targets) {
			
			// Check that target Host is capable and has enough capacity left to host the VM, 
			// and also that it will not exceed the target utilization.
			if (HostData.canHost(request.getVMDescription().getCores(), request.getVMDescription().getCoreCapacity(), reqResources, target.getSandboxStatus(), target.getHostDescription()) &&
				(target.getSandboxStatus().getResourcesInUse().getCpu() + request.getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
				
				// Add a dummy placeholder VM to keep track of placed VM resource requirements.
				target.getSandboxStatus().instantiateVm(new VmStatus(request.getVMDescription().getCores(),	request.getVMDescription().getCoreCapacity(), reqResources));
				
				return new InstantiateVmAction(target, request, event);
			}
		}
		
		return null;
	}
	
	protected ArrayList<InstantiateVmAction> placeVmsApart(ArrayList<VmAllocationRequest> antiAffinitySet,	Collection<HostData> targets, PlacementRequestEvent event) {
		ArrayList<InstantiateVmAction> actions = new ArrayList<InstantiateVmAction>();
		
		// Create copy of target hosts' list for manipulation.
		Collection<HostData> hosts = new ArrayList<HostData>(targets);
		
		for (VmAllocationRequest request : antiAffinitySet) {
			
			Resources reqResources = new Resources();
			reqResources.setCpu(request.getCpu());
			reqResources.setMemory(request.getMemory());
			reqResources.setBandwidth(request.getBandwidth());
			reqResources.setStorage(request.getStorage());
			
			HostData targetHost = null;
			for (HostData target : hosts) {
				
				// Check that target Host is capable and has enough capacity left to host the VM, 
				// and also that it will not exceed the target utilization.
				if (HostData.canHost(request.getVMDescription().getCores(), request.getVMDescription().getCoreCapacity(), reqResources, target.getSandboxStatus(), target.getHostDescription()) &&
					(target.getSandboxStatus().getResourcesInUse().getCpu() + request.getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
					
					targetHost = target;
					
					// Add a dummy placeholder VM to keep track of placed VM resource requirements.
					target.getSandboxStatus().instantiateVm(new VmStatus(request.getVMDescription().getCores(),	request.getVMDescription().getCoreCapacity(), reqResources));
					
					break;
				}
			}
			
			if (null != targetHost) {
				actions.add(new InstantiateVmAction(targetHost, request, event));
				// Remove host from target hosts' list, so that it is not considered again here.
				hosts.remove(targetHost);
			}
			else
				return null;
		}
		
		assert antiAffinitySet.size() == actions.size();
		assert targets.size() == hosts.size() + actions.size();
		
		return actions;
	}
	
	protected ArrayList<InstantiateVmAction> placeVmsTogether(ArrayList<VmAllocationRequest> affinitySet, Collection<HostData> targets, PlacementRequestEvent event) {
		ArrayList<InstantiateVmAction> actions = new ArrayList<InstantiateVmAction>();
		
		int maxReqCores = 0;
		int maxReqCoreCapacity = 0;
		int totalCpu = 0;
		int totalMemory = 0;
		int totalBandwidth = 0;
		int totalStorage = 0;
		for (VmAllocationRequest request : affinitySet) {
			if (request.getVMDescription().getCores() > maxReqCores)
				maxReqCores = request.getVMDescription().getCores();
			
			if (request.getVMDescription().getCoreCapacity() > maxReqCoreCapacity)
				maxReqCoreCapacity = request.getVMDescription().getCoreCapacity();
			
			totalCpu += request.getCpu();
			totalMemory += request.getMemory();
			totalBandwidth += request.getBandwidth();
			totalStorage += request.getStorage();
		}
		Resources totalReqResources = new Resources(totalCpu, totalMemory, totalBandwidth, totalStorage);
		
		for (HostData target : targets) {
			
			// Check that target Host is capable and has enough capacity left to host the VM, 
			// and also that it will not exceed the target utilization.
			if (HostData.canHost(maxReqCores, maxReqCoreCapacity, totalReqResources, target.getSandboxStatus(), target.getHostDescription()) &&
				(target.getSandboxStatus().getResourcesInUse().getCpu() + totalCpu) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
				
				for (VmAllocationRequest request : affinitySet) {
					
					Resources reqResources = new Resources();
					reqResources.setCpu(request.getCpu());
					reqResources.setMemory(request.getMemory());
					reqResources.setBandwidth(request.getBandwidth());
					reqResources.setStorage(request.getStorage());
					
					// Add dummy placeholder VM to keep track of placed VM' resource requirements.
					target.getSandboxStatus().instantiateVm(new VmStatus(request.getVMDescription().getCores(),	request.getVMDescription().getCoreCapacity(), reqResources));
					
					actions.add(new InstantiateVmAction(target, request, event));
				}
				
				return actions;
			}
		}
		
		return null;
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
