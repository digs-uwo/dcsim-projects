package edu.uwo.csd.dcsim.projects.hierarchical.manfi2014;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent.PowerState;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.HostDataComparator;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.management.events.ApplicationPlacementEvent;
import edu.uwo.csd.dcsim.management.events.InstantiateVmEvent;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;

/**
 * This policy places single-vm applications, one at a time, in a data centre.
 * 
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
public class SingleVmAppPlacementPolicyFFMHybrid extends Policy {

	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	
	/**
	 * Creates an instance of VmPlacementPolicyGreedy.
	 */
	public SingleVmAppPlacementPolicyFFMHybrid(double lowerThreshold, double upperThreshold, double targetUtilization) {
		addRequiredCapability(HostPoolManager.class);
		
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}
	
	/**
	 * This method processes only one VM Placement request per call. That is, if a list of 
	 * placement request is sent, only the first of said requests will be processed and the 
	 * rest will be ignored.
	 */
	public void execute(ApplicationPlacementEvent event) {
		// Get the first and only application to place and its (single) VM allocation request.
		Application app = event.getApplications().get(0);
		VmAllocationRequest allocationRequest = app.createInitialVmRequests().get(0);
		
		if (!this.searchForVmPlacementTarget(allocationRequest)) {
			event.setFailed(true);
			
			// Record failure to complete placement request.
			if (simulation.isRecordingMetrics()) {
				simulation.getSimulationMetrics().getApplicationMetrics().incrementApplicationPlacementsFailed();
			}
		}
	}
	
	/**
	 * Places a VM in the first non-stressed host with enough spare capacity to take the VM 
	 * without exceeding the _targetUtilization_ threshold.
	 */
	protected boolean searchForVmPlacementTarget(VmAllocationRequest request) {
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
		
		HostData targetHost = null;
		for (HostData target : targets) {
			Resources reqResources = new Resources();
			reqResources.setCpu(request.getCpu());
			reqResources.setMemory(request.getMemory());
			reqResources.setBandwidth(request.getBandwidth());
			reqResources.setStorage(request.getStorage());
			
			// Check that target host is capable and has enough capacity left to host the VM, 
			// and also that it will not exceed the target utilization.
			if (HostData.canHost(request.getVMDescription().getCores(), 
					request.getVMDescription().getCoreCapacity(), 
					reqResources,
					target.getSandboxStatus(),
					target.getHostDescription()) &&	
			 	(target.getSandboxStatus().getResourcesInUse().getCpu() + request.getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
				
				targetHost = target;
				
				// Add a dummy placeholder VM to keep track of placed VM resource requirements.
				target.getSandboxStatus().instantiateVm(
						new VmStatus(request.getVMDescription().getCores(),
								request.getVMDescription().getCoreCapacity(),
								reqResources));
				
				// Invalidate this host status, as we know it to be incorrect until the next status update arrives.
				target.invalidateStatus(simulation.getSimulationTime());
				
				break;
			}
		}
		
		if (targetHost != null) {
			this.sendVm(request, targetHost);
			return true;
		}
		
		return false;
	}
	
	private long sendVm(VmAllocationRequest vmAllocationRequest, HostData host) {
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
	
	/**
	 * Sorts Partially-utilized hosts in increasing order by <CPU utilization, 
	 * power efficiency>, Underutilized hosts in decreasing order by 
	 * <CPU utilization, power efficiency>, and Empty hosts in decreasing 
	 * order by <power efficiency, power state>.
	 * 
	 * Returns Partially-utilized, Underutilized and Empty hosts in that order.
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
