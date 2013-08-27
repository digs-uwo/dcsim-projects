package edu.uwo.csd.dcsim.projects.applicationManagement.policies;

import java.util.ArrayList;
import java.util.Collection;

import edu.uwo.csd.dcsim.application.*;
import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.action.InstantiateVmAction;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.management.events.ApplicationPlacementEvent;
import edu.uwo.csd.dcsim.projects.applicationManagement.ApplicationManagementMetrics;
import edu.uwo.csd.dcsim.projects.applicationManagement.events.*;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;
import edu.uwo.csd.dcsim.vm.VmDescription;

public class ApplicationPlacementPolicy extends Policy {

	public ApplicationPlacementPolicy() {
		addRequiredCapability(HostPoolManager.class);
	}
	
	public void execute(ApplicationPlacementEvent event) {
		
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		
		Application application = event.getApplication();
		
		ArrayList<VmAllocationRequest> allocationRequests = application.createInitialVmRequests();
		
		if (!place(allocationRequests, hostPool.getHosts(), event)) {
			simulation.getSimulationMetrics().getApplicationMetrics().incrementApplicationPlacementsFailed();
			event.setFailed(true);
		}
		
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
		
		//filter out invalid host status
		Collection<HostData> targets = new ArrayList<HostData>(); 
		for (HostData host : hosts) {
			if (host.isStatusValid()) {
				targets.add(host);
			}
		}
				
		//reset the sandbox host status to the current host status
		for (HostData target : targets) {
			target.resetSandboxStatusToCurrent();
		}
		
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
						(target.getHostDescription().getResourceCapacity().getCpu() - target.getSandboxStatus().getCpuAllocated()) >= request.getCpu()) //effectively disable overcommitting for initial placement
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
