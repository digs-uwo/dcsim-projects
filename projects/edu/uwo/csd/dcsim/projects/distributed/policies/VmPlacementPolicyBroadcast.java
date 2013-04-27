package edu.uwo.csd.dcsim.projects.distributed.policies;

import java.util.ArrayList;
import java.util.Map.Entry;

import edu.uwo.csd.dcsim.core.metrics.CountMetric;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent.PowerState;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.action.MigrationAction;
import edu.uwo.csd.dcsim.management.events.InstantiateVmEvent;
import edu.uwo.csd.dcsim.management.events.ShutdownVmEvent;
import edu.uwo.csd.dcsim.management.events.VmPlacementEvent;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.HostManagerBroadcast;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.HostPoolManagerBroadcast;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.HostManagerBroadcast.ManagementState;
import edu.uwo.csd.dcsim.projects.distributed.events.AcceptOfferEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.BidVmEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.AdvertiseVmEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.EvictionEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.HostPowerOnEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.HostShuttingDownEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.RejectOfferEvent;
import edu.uwo.csd.dcsim.vm.VMAllocationRequest;

public class VmPlacementPolicyBroadcast extends Policy {

	public static final String HOST_POWER_ON_METRIC = "hostPowerOn";
	public static final String SERVICES_RECEIVED = "servicesReceived";
	public static final String SERVICES_PLACED = "servicesPlaced";
	public static final String SERVICE_PLACEMENT_FAILED = "servicePlacementsFailed";
	
	private static final int PLACEMENT_ATTEMPT_TIMEOUT = 2; //the number of attempts to place a VM
	private static final int PLACEMENT_WAIT_TIME = 1000; //the number of milliseconds to wait to place a VM
	private static final int BOOT_WAIT_TIME = 45000; //the number of milliseconds to wait to place a VM

	private double lower;
	private double upper;
	private double target;
	
	public VmPlacementPolicyBroadcast(double lower, double upper, double target) {
		addRequiredCapability(HostPoolManagerBroadcast.class);
		
		this.lower = lower;
		this.upper = upper;
		this.target = target;
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
		
		if (bootNewHost && !hostPool.getPoweredOffHosts().isEmpty()) {
			for (Entry<VmStatus, Integer> entry : hostPool.getRequestCounter().entrySet()) {
				entry.setValue(2);
			}
			
			Host poweredOffHost = hostPool.getPoweredOffHosts().get(simulation.getRandom().nextInt(hostPool.getPoweredOffHosts().size()));
			simulation.sendEvent(new PowerStateEvent(poweredOffHost, PowerState.POWER_ON));
			CountMetric.getMetric(simulation, HOST_POWER_ON_METRIC + "-" + this.getClass().getSimpleName()).incrementCount();
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
			hostPool.getVmBidMap().put(vm, new ArrayList<BidVmEvent>());
			hostPool.getRequestCounter().put(vm, PLACEMENT_ATTEMPT_TIMEOUT);
			
			simulation.sendEvent(new AdvertiseVmEvent(hostPool.getBroadcastingGroup(), vm, manager));
			simulation.sendEvent(new EvictionEvent(manager, vm), simulation.getSimulationTime() + PLACEMENT_WAIT_TIME);
			
			CountMetric.getMetric(simulation, SERVICES_RECEIVED).incrementCount();
		}
	}
	
	
	public void execute(EvictionEvent event) {

		HostPoolManagerBroadcast hostPool = manager.getCapability(HostPoolManagerBroadcast.class);
		
		VMAllocationRequest request = hostPool.getRequestMap().get(event.getVm());
		
		//check for accepts
		ArrayList<BidVmEvent> vmBids = hostPool.getVmBidMap().get(event.getVm());
		
		if (vmBids.size() > 0) {
			
				//accept the request from the host with the highest utilization
				BidVmEvent target = null;
				double targetUtil = -1;
//				for (AcceptVmEvent acceptEvent : hostManager.getVmAccepts()) {
//					double util = acceptEvent.getHostStatus().getResourcesInUse().getCpu() / acceptEvent.getHost().getTotalCpu();
//					if (util > targetUtil) {
//						target = acceptEvent;
//						targetUtil = util;
//					}
//				}
				//accept the request from the host with the lowest utilization above the lower threshold, if possible
				for (BidVmEvent acceptEvent : vmBids) {
					double util = acceptEvent.getHostStatus().getResourcesInUse().getCpu() / acceptEvent.getHost().getTotalCpu();
					if (targetUtil == -1) {
						target = acceptEvent;
						targetUtil = util;
					} else if (util < targetUtil && util >= lower) {
						target = acceptEvent;
						targetUtil = util;
					} else if (util > targetUtil && targetUtil < lower) {
						target = acceptEvent;
						targetUtil = util;
					}
				}
				
				if (target != null) {
					
					
					InstantiateVmEvent instantiateEvent = new InstantiateVmEvent(target.getHostManager(), request);
					simulation.sendEvent(instantiateEvent);
					
					//send offer accept messsage
					simulation.sendEvent(new AcceptOfferEvent(target.getHostManager()));
					
					hostPool.getRequestMap().remove(event.getVm());
					hostPool.getRequestCounter().remove(event.getVm());
					hostPool.getVmBidMap().remove(event.getVm());
					
					CountMetric.getMetric(simulation, SERVICES_PLACED).incrementCount();

				} else {
					//Should not happen, exists to catch programming error
					throw new RuntimeException("Failed to select target VM from bidding Hosts for placement. Should not happen.");
				}
				
				//send out rejection messages to other hosts
				for (BidVmEvent bidEvent : vmBids) {
					if (bidEvent != target) {
						simulation.sendEvent(new RejectOfferEvent(bidEvent.getHostManager()));
					}
				}
			
		} else {
			
			if (hostPool.getRequestCounter().get(event.getVm()) > 0) {
				//retry eviction
				
				//decrement evicting counter
				hostPool.getRequestCounter().put(event.getVm(), hostPool.getRequestCounter().get(event.getVm()) - 1);
				
				//resend advertise message
				simulation.sendEvent(new AdvertiseVmEvent(hostPool.getBroadcastingGroup(), event.getVm(), manager));
				simulation.sendEvent(new EvictionEvent(manager, event.getVm()), simulation.getSimulationTime() + PLACEMENT_WAIT_TIME);
				
				if (hostPool.getLastBoot() < simulation.getSimulationTime() + BOOT_WAIT_TIME) {
					Host poweredOffHost = hostPool.getPoweredOffHosts().get(simulation.getRandom().nextInt(hostPool.getPoweredOffHosts().size()));
					simulation.sendEvent(new PowerStateEvent(poweredOffHost, PowerState.POWER_ON));
					CountMetric.getMetric(simulation, HOST_POWER_ON_METRIC + "-" + this.getClass().getSimpleName()).incrementCount();
					
					hostPool.setLastBoot(simulation.getSimulationTime());
					hostPool.getRequestCounter().put(event.getVm(), PLACEMENT_ATTEMPT_TIMEOUT);
				}
				
			} else {
				//placement has failed
				hostPool.getRequestMap().remove(event.getVm());
				hostPool.getRequestCounter().remove(event.getVm());
				hostPool.getVmBidMap().remove(event.getVm());
				
				CountMetric.getMetric(simulation, SERVICE_PLACEMENT_FAILED).incrementCount();
			}
		}
	}
	
	
	public void execute(BidVmEvent event) {
		HostPoolManagerBroadcast hostPool = manager.getCapability(HostPoolManagerBroadcast.class);
		
		VMAllocationRequest request = hostPool.getRequestMap().get(event.getVm());
		if (request != null) {
			hostPool.getVmBidMap().get(event.getVm()).add(event);
		} else {
			//send rejection message
			simulation.sendEvent(new RejectOfferEvent(event.getHostManager()));
		}
		
//		VMAllocationRequest request = hostPool.getRequestMap().get(event.getVm());
//		if (request != null) {
//			InstantiateVmEvent instantiateEvent = new InstantiateVmEvent(event.getHostManager(), request);
//			simulation.sendEvent(instantiateEvent);
//			
//			//send offer accept messsage
//			simulation.sendEvent(new AcceptOfferEvent(event.getHostManager()));
//			
//			hostPool.getRequestMap().remove(event.getVm());
//			hostPool.getRequestCounter().remove(event.getVm());
//			
//			CountMetric.getMetric(simulation, SERVICES_PLACED).incrementCount();
//		} else {
//			//send rejection message
//			simulation.sendEvent(new RejectOfferEvent(event.getHostManager()));
//		}
		
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
