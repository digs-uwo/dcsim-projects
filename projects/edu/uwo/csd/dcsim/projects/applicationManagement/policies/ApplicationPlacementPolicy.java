package edu.uwo.csd.dcsim.projects.applicationManagement.policies;

import java.util.ArrayList;
import java.util.Collection;

import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.action.InstantiateVmAction;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.events.*;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;

public class ApplicationPlacementPolicy extends Policy {

	public ApplicationPlacementPolicy() {
		addRequiredCapability(HostPoolManager.class);
	}
	
	public void execute(ApplicationPlacementEvent event) {
		
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		
		Application application = event.getApplication();
		
		//filter out invalid host status
		Collection<HostData> hosts = new ArrayList<HostData>(); 
		for (HostData host : hostPool.getHosts()) {
			if (host.isStatusValid()) {
				hosts.add(host);
			}
		}
				
		//reset the sandbox host status to the current host status
		for (HostData host : hosts) {
			host.resetSandboxStatusToCurrent();
		}
		
		ArrayList<VmAllocationRequest> allocationRequests = application.createInitialVmRequests();
		
		for (VmAllocationRequest request : allocationRequests) {
			
			HostData allocatedHost = null;
			
			for (HostData target : hosts) {
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
				InstantiateVmAction instantiateVmAction = new InstantiateVmAction(allocatedHost, request, event);
				instantiateVmAction.execute(simulation, this);
			}
		}
		
		
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
