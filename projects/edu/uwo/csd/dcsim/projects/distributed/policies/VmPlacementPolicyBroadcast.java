package edu.uwo.csd.dcsim.projects.distributed.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.core.metrics.CountMetric;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.host.comparator.HostComparator;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent.PowerState;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.events.InstantiateVmEvent;
import edu.uwo.csd.dcsim.management.events.ShutdownVmEvent;
import edu.uwo.csd.dcsim.management.events.VmPlacementEvent;
import edu.uwo.csd.dcsim.projects.distributed.Eviction;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.HostPoolManagerBroadcast;
import edu.uwo.csd.dcsim.projects.distributed.comparators.ResourceOfferComparator;
import edu.uwo.csd.dcsim.projects.distributed.events.AcceptOfferEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.ResourceOfferEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.RequestResourcesEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.EvictionEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.HostPowerOnEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.HostShuttingDownEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.RejectOfferEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.UpdatePowerStateListEvent;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;

public class VmPlacementPolicyBroadcast extends Policy {

	public static final String HOST_POWER_ON_METRIC = "hostPowerOn";
	public static final String SERVICES_RECEIVED = "servicesReceived";
	public static final String SERVICES_PLACED = "servicesPlaced";
	public static final String SERVICE_PLACEMENT_FAILED = "servicePlacementsFailed";
	
	private static final long PLACEMENT_WAIT_TIME = 500; //the number of milliseconds to wait to place a VM
	private static final long BOOT_WAIT_TIME = SimTime.minutes(1); //the number of milliseconds to wait after booting a host, before booting anothor
	
	private double lower;
	private double upper;
	private double target;
	
	public VmPlacementPolicyBroadcast(double lower, double upper, double target) {
		addRequiredCapability(HostPoolManagerBroadcast.class);
		
		this.lower = lower;
		this.upper = upper;
		this.target = target;
	}
	
	public void execute(VmPlacementEvent event) {

		HostPoolManagerBroadcast hostPool = manager.getCapability(HostPoolManagerBroadcast.class);
				
		//TODO there is a very good chance this will fail with multiple simultaneous VM requests
		for (VmAllocationRequest request : event.getVMAllocationRequests()) {
			//send resource request message
			Resources resources = new Resources();
			resources.setCpu(request.getCpu());
			resources.setMemory(request.getMemory());
			resources.setBandwidth(request.getBandwidth());
			resources.setStorage(request.getStorage());
			
			VmStatus vm = new VmStatus(request.getVMDescription().getCores(),
					request.getVMDescription().getCoreCapacity(),
					resources);
			
			Eviction eviction = new Eviction();
			eviction.setVmList(vm);
			
			hostPool.addEviction(eviction, request);
			
			simulation.sendEvent(new RequestResourcesEvent(hostPool.getBroadcastingGroup(), resources, eviction, manager, RequestResourcesEvent.AdvertiseReason.PLACEMENT));
			simulation.sendEvent(new EvictionEvent(manager, eviction), simulation.getSimulationTime() + PLACEMENT_WAIT_TIME);
			
			CountMetric.getMetric(simulation, SERVICES_RECEIVED).incrementCount();
		}
	}
	
	
	public void execute(EvictionEvent event) {

		HostPoolManagerBroadcast hostPool = manager.getCapability(HostPoolManagerBroadcast.class);
		
		Eviction eviction = event.getEviction();
			
		VmStatus vm = eviction.getVmList().get(0);
		
		boolean targetFound = false;
		
		if (eviction.getResourceOffers().size() > 0) {
			//Sort target hosts
			ArrayList<ResourceOfferEvent> targets = sortTargetHosts(eviction.getResourceOffers());
			
			//loop until a target is found
			ResourceOfferEvent target = null;
			
			for (ResourceOfferEvent hostOffer : targets) {
				Host host = hostOffer.getHost(); //to check capabilities. Can be assumed known by this host manager, since capabilities are static.
				HostStatus hostStatus = hostOffer.getHostStatus();
				Resources resourcesOffered = hostOffer.getResourcesOffered();
				
				//check capability & capacity
				if (canHost(host, hostStatus, vm) &&
						vm.getResourcesInUse().getCpu() <= resourcesOffered.getCpu() &&
						vm.getResourcesInUse().getMemory() <= resourcesOffered.getMemory() &&
						vm.getResourcesInUse().getBandwidth() <= resourcesOffered.getBandwidth() &&
						vm.getResourcesInUse().getStorage() <= resourcesOffered.getStorage()
						) {
					
					target = hostOffer;
					break;
				}
			}
			
			if (target != null) {
			
				targetFound = true;
				
				InstantiateVmEvent instantiateEvent = new InstantiateVmEvent(target.getHostManager(), hostPool.getRequest(eviction));
				simulation.sendEvent(instantiateEvent);
				
				//send offer accept messsage
				simulation.sendEvent(new AcceptOfferEvent(target.getHostManager(), target));
				
				hostPool.clearEviction(eviction);
				
				CountMetric.getMetric(simulation, SERVICES_PLACED).incrementCount();

			}
			
			//send out rejection messages to other hosts
			for (ResourceOfferEvent offerEvent : eviction.getResourceOffers()) {
				if (offerEvent != target) {
					simulation.sendEvent(new RejectOfferEvent(offerEvent.getHostManager(), offerEvent));
				}
			}
		}
		
		if (!targetFound) {
			if ((hostPool.getLastBoot() < simulation.getSimulationTime() + BOOT_WAIT_TIME) && (hostPool.getPoweredOffHosts().size() > 0)) {
				
				//look for a new host to boot
				
				ArrayList<Host> poweredOffHosts = new ArrayList<Host>();
				poweredOffHosts.addAll(hostPool.getPoweredOffHosts());
				
				//Sort Empty hosts in decreasing order by <power efficiency, power state>.
				Collections.sort(poweredOffHosts, HostComparator.getComparator(HostComparator.EFFICIENCY));
				Collections.reverse(poweredOffHosts);
				
				//Find target
				Host target = null;
				for (Host host : poweredOffHosts) {
					if (canHost(host, vm)) {
						target = host;
						break;
					}
				}
				
				if (target != null) {				
					simulation.sendEvent(new PowerStateEvent(target, PowerState.POWER_ON));
					InstantiateVmEvent instantiateEvent = new InstantiateVmEvent(target.getAutonomicManager(), hostPool.getRequest(eviction));
					simulation.sendEvent(instantiateEvent);
					simulation.sendEvent(new UpdatePowerStateListEvent(target.getAutonomicManager(), hostPool.getPoweredOffHosts()));
					
					hostPool.clearEviction(eviction);
					CountMetric.getMetric(simulation, SERVICES_PLACED).incrementCount();
					
					CountMetric.getMetric(simulation, HOST_POWER_ON_METRIC + "-" + this.getClass().getSimpleName()).incrementCount();
					
					hostPool.setLastBoot(simulation.getSimulationTime());
				}

			} else {
				hostPool.clearEviction(eviction);
				CountMetric.getMetric(simulation, SERVICE_PLACEMENT_FAILED).incrementCount();
			}
			
		}
			
	}
	
	
	public void execute(ResourceOfferEvent event) {
		HostPoolManagerBroadcast hostPool = manager.getCapability(HostPoolManagerBroadcast.class);
		
		Eviction eviction = event.getEviction();
		
		if (hostPool.getEvictions().contains(eviction)) {
			eviction.getResourceOffers().add(event);
		} else {
			//send rejection message
			simulation.sendEvent(new RejectOfferEvent(event.getHostManager(), event));
		}
		
	}
	
	public void execute(ShutdownVmEvent event) {
		HostPoolManagerBroadcast hostPool = manager.getCapability(HostPoolManagerBroadcast.class);
		AutonomicManager hostManager = hostPool.getHost(event.getHostId()).getHostManager();
		
		event.setLog(false);
		
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
	
	private ArrayList<ResourceOfferEvent> sortTargetHosts(ArrayList<ResourceOfferEvent> targets) {
		ArrayList<ResourceOfferEvent> sorted = new ArrayList<ResourceOfferEvent>();
		
		//classify into partially utilized and under utilized
		ArrayList<ResourceOfferEvent> partiallyUtilized = new ArrayList<ResourceOfferEvent>();
		ArrayList<ResourceOfferEvent> underUtilized = new ArrayList<ResourceOfferEvent>();
		
		for (ResourceOfferEvent host : targets) {
			//note that stressed and powered off hosts do not bid, which represents a difference from the centralized implementation
			if (host.getHostStatus().getResourcesInUse().getCpu() / host.getHost().getResourceManager().getTotalCpu() < lower) {
				underUtilized.add(host);
			} else {
				partiallyUtilized.add(host);
			}
		}
		
		// Sort Partially-Utilized hosts in increasing order by <CPU in use, power efficiency>.
		Collections.sort(partiallyUtilized, ResourceOfferComparator.getComparator(ResourceOfferComparator.CPU_IN_USE, ResourceOfferComparator.EFFICIENCY));
		
		// Sort Underutilized hosts in decreasing order by <CPU in use, power efficiency>.
		Collections.sort(underUtilized, ResourceOfferComparator.getComparator(ResourceOfferComparator.CPU_IN_USE, ResourceOfferComparator.EFFICIENCY));
		Collections.reverse(underUtilized);
		
		sorted.addAll(partiallyUtilized);
		sorted.addAll(underUtilized);
		
		return sorted;
	}
	
	/**
	 * Determine if this host is capable of hosting the given VM
	 * 
	 * @param host
	 * @param hostStatus
	 * @param vm
	 * @return
	 */
	private boolean canHost(Host host, HostStatus hostStatus, VmStatus vm) {
		//check capabilities
		if (host.getCpuCount() * host.getCoreCount() < vm.getCores() ||
				host.getCoreCapacity() < vm.getCoreCapacity()) {
			return false;
		}
		
		//check remaining capacity
		Resources resourcesInUse = hostStatus.getResourcesInUse();
		if (host.getResourceManager().getTotalCpu() - resourcesInUse.getCpu() < vm.getResourcesInUse().getCpu())
			return false;
		if (host.getResourceManager().getTotalMemory() - resourcesInUse.getMemory() < vm.getResourcesInUse().getMemory())
			return false;
		if (host.getResourceManager().getTotalBandwidth() - resourcesInUse.getBandwidth() < vm.getResourcesInUse().getBandwidth())
			return false;
		if (host.getResourceManager().getTotalStorage() - resourcesInUse.getStorage() < vm.getResourcesInUse().getStorage())
			return false;
		
		return true;
	}
	
	private boolean canHost(Host host, VmStatus vm) {
		//check capabilities
		if (host.getCpuCount() * host.getCoreCount() < vm.getCores() ||
				host.getCoreCapacity() < vm.getCoreCapacity()) {
			return false;
		}
		
		//check capacity
		if (host.getResourceManager().getTotalCpu() < vm.getResourcesInUse().getCpu())
			return false;
		if (host.getResourceManager().getTotalMemory() < vm.getResourcesInUse().getMemory())
			return false;
		if (host.getResourceManager().getTotalBandwidth() < vm.getResourcesInUse().getBandwidth())
			return false;
		if (host.getResourceManager().getTotalStorage() < vm.getResourcesInUse().getStorage())
			return false;
		
		return true;
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
