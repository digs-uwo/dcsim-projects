package edu.uwo.csd.dcsim.projects.distributed.policies;

import java.util.Map.Entry;

import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent.PowerState;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.events.InstantiateVmEvent;
import edu.uwo.csd.dcsim.management.events.ShutdownVmEvent;
import edu.uwo.csd.dcsim.management.events.VmPlacementEvent;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.HostPoolManagerBroadcast;
import edu.uwo.csd.dcsim.projects.distributed.events.AcceptVmEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.AdvertiseVmEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.HostPowerOnEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.HostShuttingDownEvent;
import edu.uwo.csd.dcsim.vm.VMAllocationRequest;

public class VmPlacementPolicyBroadcast extends Policy {

	public VmPlacementPolicyBroadcast() {
		addRequiredCapability(HostPoolManagerBroadcast.class);
	}
	
	public void execute() {
		//check for incomplete placements
		HostPoolManagerBroadcast hostPool = manager.getCapability(HostPoolManagerBroadcast.class);
		
		boolean bootNewHost = false;
		for (Entry<VmStatus, Integer> entry : hostPool.getRequestCounter().entrySet()) {
			entry.setValue(entry.getValue() - 1);
			if (entry.getValue() == 0) {
				bootNewHost = true;
			} else {
				simulation.sendEvent(new AdvertiseVmEvent(hostPool.getBroadcastingGroup(), entry.getKey(), manager));	
			}
		}
		
		if (bootNewHost) {
			for (Entry<VmStatus, Integer> entry : hostPool.getRequestCounter().entrySet()) {
				entry.setValue(2);
			}
			
			Host poweredOffHost = hostPool.getPoweredOffHosts().get(simulation.getRandom().nextInt(hostPool.getPoweredOffHosts().size()));
			simulation.sendEvent(new PowerStateEvent(poweredOffHost, PowerState.POWER_ON));
		}
		
	}
	
	public void execute(VmPlacementEvent event) {
		
		HostPoolManagerBroadcast hostPool = manager.getCapability(HostPoolManagerBroadcast.class);
		
		for (VMAllocationRequest request : event.getVMAllocationRequests()) {
			//send advertise message
			Resources resources = new Resources();
			resources.setCpu(request.getCpu());
			resources.setMemory(request.getMemory());
			resources.setBandwidth(request.getBandwidth());
			resources.setStorage(request.getStorage());
			
			VmStatus vm = new VmStatus(request.getVMDescription().getCores(),
					request.getVMDescription().getCoreCapacity(),
					resources);
			hostPool.getRequestMap().put(vm, request);
			hostPool.getRequestCounter().put(vm, 2);
			
			simulation.sendEvent(new AdvertiseVmEvent(hostPool.getBroadcastingGroup(), vm, manager));	
		}
	}
	
	public void execute(AcceptVmEvent event) {
		HostPoolManagerBroadcast hostPool = manager.getCapability(HostPoolManagerBroadcast.class);
		
		VMAllocationRequest request = hostPool.getRequestMap().get(event.getVm());
		if (request != null) {
			InstantiateVmEvent instantiateEvent = new InstantiateVmEvent(event.getHostManager(), request);
			simulation.sendEvent(instantiateEvent);
		}
		
	}
	
	public void execute(ShutdownVmEvent event) {
		HostPoolManagerBroadcast hostPool = manager.getCapability(HostPoolManagerBroadcast.class);
		AutonomicManager hostManager = hostPool.getHost(event.getHostId()).getHostManager();
		
		ShutdownVmEvent shutdownEvent = new ShutdownVmEvent(hostManager, event.getHostId(), event.getVmId()); 
		event.addEventInSequence(shutdownEvent);
		simulation.sendEvent(shutdownEvent);
	}
	
	public void execute(HostShuttingDownEvent event) {
		//store in list of powered off hosts
		HostPoolManagerBroadcast hostPool = manager.getCapability(HostPoolManagerBroadcast.class);
		
		hostPool.getPoweredOffHosts().add(event.getHost());
	}
	
	public void execute(HostPowerOnEvent event) {
		//remove from list of powered off hosts
		HostPoolManagerBroadcast hostPool = manager.getCapability(HostPoolManagerBroadcast.class);
		
		hostPool.getPoweredOffHosts().remove(event.getHost());
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
