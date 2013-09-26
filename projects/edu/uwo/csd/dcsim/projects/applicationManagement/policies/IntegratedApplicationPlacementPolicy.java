package edu.uwo.csd.dcsim.projects.applicationManagement.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import edu.uwo.csd.dcsim.application.*;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.HostDataComparator;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.action.InstantiateVmAction;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.management.events.ApplicationPlacementEvent;
import edu.uwo.csd.dcsim.projects.applicationManagement.ApplicationManagementMetrics;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.*;
import edu.uwo.csd.dcsim.projects.applicationManagement.events.*;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;
import edu.uwo.csd.dcsim.vm.VmDescription;

public class IntegratedApplicationPlacementPolicy extends Policy {

	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	
	public IntegratedApplicationPlacementPolicy(double lowerThreshold, double upperThreshold, double targetUtilization) {
		addRequiredCapability(DataCentreManager.class);
		
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}
	
	public void execute(ApplicationPlacementEvent event) {
		
		HostPoolManager hostPool = manager.getCapability(DataCentreManager.class);
		Collection<HostData> hosts = hostPool.getHosts();
		
		ArrayList<Application> applications = event.getApplications();
		
		ArrayList<InstantiateVmAction> actions = new ArrayList<InstantiateVmAction>();
		
		//reset the sandbox host status to the current host status
		for (HostData host : hosts) {
			host.resetSandboxStatusToCurrent();
		}
		
		//place each application individually
		for (Application application : applications) {
			placeApplication(application);
		}
		
		
//		//get task allocation requests
//		ArrayList<ArrayList<VmAllocationRequest>> taskAllocationRequests = new ArrayList<ArrayList<VmAllocationRequest>>();
//		for (Application application : applications) {
//			for (Task task : application.getTasks()) {
//				taskAllocationRequests.add(task.createInitialVmRequests());
//			}
//		}
		
//		//order allocation requests by alternating Task
//		ArrayList<VmAllocationRequest> allocationRequests = new ArrayList<VmAllocationRequest>();
//		boolean done = false;
//		while (!done) {
//			done = true;
//			for (ArrayList<VmAllocationRequest> taskRequests : taskAllocationRequests) {
//				if (!taskRequests.isEmpty()) {
//					allocationRequests.add(taskRequests.get(0));
//					taskRequests.remove(0);
//					if (!taskRequests.isEmpty()) done = false;
//				}
//			}
//		}
		
//		if (!place(allocationRequests, hostPool.getHosts(), event)) {
//			simulation.getSimulationMetrics().getApplicationMetrics().incrementApplicationPlacementsFailed();
//			event.setFailed(true);
//		}
		
	}
	
	private void placeApplication(Application application) {
		
		ArrayList<ArrayList<VmAllocationRequest>> taskAllocationRequests = new ArrayList<ArrayList<VmAllocationRequest>>();
		for (Task task : application.getTasks()) {
			taskAllocationRequests.add(task.createInitialVmRequests());
		}
		
		//select Rack for deployment
		
		
		//deploy into rack, spreading task instances
		
	}
	
	public void execute(TaskInstancePlacementEvent event) {
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		
		Task task = event.getTask();
		
		VmAllocationRequest request = new VmAllocationRequest(new VmDescription(task));
		
		if (!place(request, hostPool.getHosts(), event)) {
			simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).instancePlacementsFailed++;
		}
		
	}
	
	private boolean place(VmAllocationRequest request, Collection<HostData> hosts, Event placementEvent) {
		ArrayList<VmAllocationRequest> requests = new ArrayList<VmAllocationRequest>();
		requests.add(request);
		return place(requests, hosts, placementEvent);
	}
	
	private boolean place(Collection<VmAllocationRequest> requests, Collection<HostData> hosts, Event placementEvent) {
		
		ArrayList<InstantiateVmAction> actions = new ArrayList<InstantiateVmAction>();
				
		//reset the sandbox host status to the current host status
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
		
		for (VmAllocationRequest request : requests) {

			HostData allocatedHost = null;
			for (HostData target : targets) {
				Resources reqResources = new Resources();
				reqResources.setCpu(request.getCpu());
				reqResources.setMemory(request.getMemory());
				reqResources.setBandwidth(request.getBandwidth());
				reqResources.setStorage(request.getStorage());

				if (HostData.canHost(request.getVMDescription().getCores(), 	//target has capability and capacity to host VM 
						request.getVMDescription().getCoreCapacity(), 
						reqResources,
						target.getSandboxStatus(),
						target.getHostDescription()) &&
						(target.getSandboxStatus().getResourcesInUse().getCpu() + request.getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization)
						{
					
					allocatedHost = target;
					
					//add a dummy placeholder VM to keep track of placed VM resource requirements
					target.getSandboxStatus().instantiateVm(
							new VmStatus(request.getVMDescription().getCores(),
									request.getVMDescription().getCoreCapacity(),
							reqResources));
					
					//invalidate this host status, as we know it to be incorrect until the next status update arrives
					target.invalidateStatus(simulation.getSimulationTime());
					
					break;
				 }
			}
			
			if (allocatedHost != null) {
				//delay actions until placements have been found for all VMs
				actions.add(new InstantiateVmAction(allocatedHost, request, placementEvent));
			} else {
				return false;
			}
		}
		
		for (InstantiateVmAction action : actions) {
			action.execute(simulation, this);
		}
		
		return true;
	}
	
	public void execute(ShutdownApplicationEvent event) {
		//TODO: handle
	}
	
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
