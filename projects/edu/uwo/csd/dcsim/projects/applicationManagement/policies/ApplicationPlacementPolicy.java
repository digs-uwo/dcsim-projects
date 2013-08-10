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
		
		place(allocationRequests, hostPool.getHosts(), event);
		
	}
	
	public void execute(TaskInstancePlacementEvent event) {
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		
		Task task = event.getTask();
		
		VmAllocationRequest request = new VmAllocationRequest(new VmDescription(task));
		
		System.out.println("place task" + task.getId() + " " + place(request, hostPool.getHosts(), event));
		
	}
	
	private boolean place(VmAllocationRequest request, Collection<HostData> hosts, Event placementEvent) {
		ArrayList<VmAllocationRequest> requests = new ArrayList<VmAllocationRequest>();
		requests.add(request);
		return place(requests, hosts, placementEvent);
	}
	
	private boolean place(Collection<VmAllocationRequest> requests, Collection<HostData> hosts, Event placementEvent) {
		
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

				if (HostData.canHost(request.getVMDescription().getCores(), 
						request.getVMDescription().getCoreCapacity(), 
						reqResources,
						target.getSandboxStatus(),
						target.getHostDescription())) {	//target has capability and capacity to host VM
					
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
				InstantiateVmAction instantiateVmAction = new InstantiateVmAction(allocatedHost, request, placementEvent);
				instantiateVmAction.execute(simulation, this);
			} else {
				return false;
			}
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
