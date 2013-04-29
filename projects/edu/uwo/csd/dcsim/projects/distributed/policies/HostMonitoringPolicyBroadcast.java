package edu.uwo.csd.dcsim.projects.distributed.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.core.metrics.CountMetric;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent;
import edu.uwo.csd.dcsim.host.events.PowerStateEvent.PowerState;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.action.MigrationAction;
import edu.uwo.csd.dcsim.management.action.ShutdownHostAction;
import edu.uwo.csd.dcsim.management.capabilities.*;
import edu.uwo.csd.dcsim.management.events.InstantiateVmEvent;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.*;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.HostManagerBroadcast.ManagementState;
import edu.uwo.csd.dcsim.projects.distributed.events.*;

public class HostMonitoringPolicyBroadcast extends Policy {

	private static final int EVICTION_ATTEMPT_TIMEOUT = 2; //the number of attempts to evict a VM
	private static final int EVICTION_WAIT_TIME = 500; //the number of milliseconds to wait to evict a VM
	private static final int EVICTION_WAIT_BACKOFF_MULTIPLE = 1; //multiply eviction wait time by this value after a failed attempt
	private static final int MONITOR_WINDOW = 5;
	private static final int SHUTDOWN_WAIT_TIME = 60000;
	
	public static final String STRESS_EVICT_FAIL = "stressEvictionFailed";
	public static final String SHUTDOWN_EVICT_FAIL = "shutdownEvictionFailed";
	public static final String STRESS_EVICT = "stressEviction";
	public static final String SHUTDOWN_EVICT = "shutdownEviction";
	
	private double lower;
	private double upper;
	private double target;
	
	public HostMonitoringPolicyBroadcast(double lower, double upper, double target) {
		addRequiredCapability(HostManagerBroadcast.class);
		
		this.lower = lower;
		this.upper = upper;
		this.target = target;
	}
	
	/**
	 * Execute on a regular interval, handling the operation of the host
	 */
	public void execute() {
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		/*
		 * Monitoring
		 */
		HostStatus hostStatus = new HostStatus(hostManager.getHost(), simulation.getSimulationTime());
		hostManager.addHistoryStatus(hostStatus, MONITOR_WINDOW);
		
		//if a migration is pending, wait until it is complete
		if ((hostStatus.getIncomingMigrationCount() > 0) || (hostStatus.getOutgoingMigrationCount() > 0)) return;
		
		/*
		 * SHUTTING DOWN 
		 * 
		 * Evict all VMs and shut down, or cancel shut down if cannot evict a VM
		 * 
		 */
		if (hostManager.getManagementState() == ManagementState.SHUTTING_DOWN) {
			//if shutting down
			
			//if evictions complete, shut down
			if (hostStatus.getVms().size() != 0) {
				
				//sort VMs
				ArrayList<VmStatus> vmList = orderVmsUnderUtilized(hostStatus.getVms());
				
				//evict first VM in list
				if (!vmList.isEmpty()) {
					CountMetric.getMetric(simulation, SHUTDOWN_EVICT).incrementCount();
					evict(hostManager, vmList.get(0), AdvertiseVmEvent.AdvertiseReason.SHUTDOWN);	
				}
				
			} else {
			
				//send shutdown message
				simulation.sendEvent(new HostShuttingDownEvent(hostManager.getBroadcastingGroup(), hostManager.getHost()));
				
				//shut down
				ShutdownHostAction shutdownAction = new ShutdownHostAction(hostManager.getHost());
				shutdownAction.execute(simulation, this);
				
			}
			

			/*
			 * NORMAL STATE (no evictions, not shutting down, not bidding)
			 * 
			 * Detect stress and underutilization
			 * 
			 */
		} else if (!hostManager.isEvicting() && hostManager.getManagementState() == ManagementState.NORMAL) {
			/*
			 * STRESSED (detect stressed state)
			 * 
			 * Trigger a VM eviction
			 *  
			 */
			if (isStressed(hostManager)) {
				//host is stressed, evict a VM

				//sort VMs
				ArrayList<VmStatus> vmList = orderVmsStressed(hostStatus.getVms(), hostManager.getHost());
				
				//evict first VM in list
				if (!vmList.isEmpty()) {
					CountMetric.getMetric(simulation, STRESS_EVICT).incrementCount();
					evict(hostManager, vmList.get(0), AdvertiseVmEvent.AdvertiseReason.STRESS);	
				}
				
			} 
			
			/*
			 * UNDERUTILIZED (detect underutilized state)
			 *
			 * Decide whether or not to switch into SHUTDOWN
			 */
			
			else if (isUnderUtilized(hostManager, hostStatus)) {
				
				//host is underutilized, decide if should switch to eviction, if so, evict a VM
				
				//if (simulation.getRandom().nextDouble() < 0.01) {
				if (hostManager.getLastShutdownEvent() + SHUTDOWN_WAIT_TIME <= simulation.getSimulationTime() &&
						getActiveHostCount(hostManager) > 1) {
					hostManager.setManagementState(ManagementState.SHUTTING_DOWN); 
					
					//sort VMs
					ArrayList<VmStatus> vmList = orderVmsUnderUtilized(hostStatus.getVms());
					
					//evict first VM in list
					if (!vmList.isEmpty()) {
						CountMetric.getMetric(simulation, SHUTDOWN_EVICT).incrementCount();
						evict(hostManager, vmList.get(0), AdvertiseVmEvent.AdvertiseReason.SHUTDOWN);	
					}
				}

			}
		}
		


	}
	
	/**
	 * Evict a single VM from this host
	 * 
	 * @param hostManager
	 * @param vm
	 */
	private void evict(HostManagerBroadcast hostManager, VmStatus vm, AdvertiseVmEvent.AdvertiseReason reason) {
		//setup eviction counter/data
		hostManager.setEvicting(EVICTION_ATTEMPT_TIMEOUT);
		hostManager.setEvictingVm(vm);
		
		//send advertise message
		simulation.sendEvent(new AdvertiseVmEvent(hostManager.getBroadcastingGroup(), vm, manager, reason));	
		simulation.sendEvent(new EvictionEvent(manager, vm), simulation.getSimulationTime() + EVICTION_WAIT_TIME);
	}
	
	public void execute(EvictionEvent event) {

		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		//check for accepts
		if (hostManager.getVmAccepts().size() > 0) {
			
			//double check that the VM is still running on the host (could have terminated)
			if (hostManager.getHost().getVMAllocation(hostManager.getEvictingVm().getId()) == null) {
				//clear eviction state
				hostManager.setEvicting(0);
				hostManager.setEvictingVm(null);
				
			} else {

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
				for (BidVmEvent acceptEvent : hostManager.getVmAccepts()) {
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
					//trigger migration
					MigrationAction migAction = new MigrationAction(manager, hostManager.getHost(), target.getHost(), target.getVm().getId());
					migAction.execute(simulation, this);					

					//send offer accept messsage
					simulation.sendEvent(new AcceptOfferEvent(target.getHostManager()));
					
					//clear eviction state
					hostManager.setEvicting(0);
					hostManager.setEvictingVm(null);
				} else {
					//Should not happen, exists to catch programming error
					throw new RuntimeException("Failed to select target VM from acception Hosts. Should not happen.");
				}
				
				//send out rejection messages to other hosts
				for (BidVmEvent acceptEvent : hostManager.getVmAccepts()) {
					if (acceptEvent != target) {
						simulation.sendEvent(new RejectOfferEvent(acceptEvent.getHostManager()));
					}
				}
			
			}
			//clear VM accepts
			hostManager.getVmAccepts().clear();
			
		} else {
			
			if (hostManager.getEvicting() > 0) {
				//retry eviction
				
				//decrement evicting counter
				hostManager.setEvicting(hostManager.getEvicting() - 1);
				
				//resend advertise message
				AdvertiseVmEvent.AdvertiseReason reason;
				if (hostManager.getManagementState() == ManagementState.SHUTTING_DOWN) {
					reason = AdvertiseVmEvent.AdvertiseReason.SHUTDOWN;
				} else {
					reason = AdvertiseVmEvent.AdvertiseReason.STRESS;
				}
				simulation.sendEvent(new AdvertiseVmEvent(hostManager.getBroadcastingGroup(), hostManager.getEvictingVm(), manager, reason));
				simulation.sendEvent(new EvictionEvent(manager, event.getVm()), simulation.getSimulationTime() + EVICTION_WAIT_TIME * EVICTION_WAIT_BACKOFF_MULTIPLE);
			} else {
				//eviction has failed			

				//if shutting down, cancel the shutdown
				if (hostManager.getManagementState() == ManagementState.SHUTTING_DOWN) {
					hostManager.setManagementState(ManagementState.NORMAL);
					
					CountMetric.getMetric(simulation, SHUTDOWN_EVICT_FAIL).incrementCount();
				} else {
					//if evicting because of stress, boot a new host
					
					//boot new host
					if (hostManager.getPoweredOffHosts().size() > 0) {
						
						Host poweredOffHost = hostManager.getPoweredOffHosts().get(simulation.getRandom().nextInt(hostManager.getPoweredOffHosts().size()));
					
						//trigger migration (MirationAction will boot host)
						MigrationAction migAction = new MigrationAction(manager, hostManager.getHost(), poweredOffHost, event.getVm().getId());
						migAction.execute(simulation, this);
						
						//clear eviction state
						hostManager.setEvicting(0);
						hostManager.setEvictingVm(null);
						
						CountMetric.getMetric(simulation, VmPlacementPolicyBroadcast.HOST_POWER_ON_METRIC + "-" + this.getClass().getSimpleName()).incrementCount();
					} else {
						CountMetric.getMetric(simulation, STRESS_EVICT_FAIL).incrementCount();						
					}
				}
				
			}
		}
	}
	
	
	public void execute(AcceptOfferEvent event) {
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		hostManager.setManagementState(ManagementState.NORMAL);
	}
	
	public void execute(RejectOfferEvent event) {
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		hostManager.setManagementState(ManagementState.NORMAL);
	}
	

	/**
	 * Receive a VM advertisement message
	 * 
	 * @param event
	 */
	public void execute(AdvertiseVmEvent event) {
		//received a VM advertisement
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		Host host = hostManager.getHost();
		HostStatus hostStatus = new HostStatus(hostManager.getHost(), simulation.getSimulationTime());
		
		if (event.getReason() == AdvertiseVmEvent.AdvertiseReason.SHUTDOWN) {
			hostManager.setLastShutdownEvent(simulation.getSimulationTime());
		}

		if (hostManager.getManagementState() == ManagementState.NORMAL && !hostManager.isEvicting()) {
			if (event.getHostManager() != manager &&
					!isStressed(hostManager) && 
					!(hostStatus.getIncomingMigrationCount() > 0) &&
					!(hostStatus.getOutgoingMigrationCount() > 0)) {

				VmStatus vm = event.getVm();
							
				//check if we are capable of hosting this VM
				if (canHost(host, hostStatus, vm) && //check capability
						((hostStatus.getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / host.getResourceManager().getTotalCpu() <= target) && //check current util < target
						((getAvgCpu(hostManager) + vm.getResourcesInUse().getCpu()) / host.getResourceManager().getTotalCpu() <= target)) { //get average util < target
					
					//send accept message
					simulation.sendEvent(new BidVmEvent(event.getHostManager(), vm, host, manager, hostStatus));
					hostManager.setManagementState(ManagementState.BIDDING);
				}

			}
		}
		
	}
	
	/**
	 * Receive a VM accept message
	 * 
	 * @param event
	 */
	public void execute(BidVmEvent event) {
		//a Host has accepted your advertised VM
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		//ensure that this VM is still being advertised
		if ((hostManager.isEvicting()) && hostManager.getEvictingVm().equals(event.getVm())) {
			hostManager.getVmAccepts().add(event);
		}

	}
	
	/**
	 * Receive a Host Shutting Down event, indicating that another host has shut itself down
	 * 
	 * @param event
	 */
	public void execute(HostShuttingDownEvent event) {
		//store in list of powered off hosts
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		if (event.getHost() != hostManager.getHost())
			hostManager.getPoweredOffHosts().add(event.getHost());

	}
	
	/**
	 * Receive a Host Power On event, indicating that another host has powered on
	 * 
	 * @param event
	 */
	public void execute(HostPowerOnEvent event) {
		//remove from list of powered off hosts
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		hostManager.getPoweredOffHosts().remove(event.getHost());
	}
	
	

	
	/**
	 * Determine if the host is 'stressed'
	 * 
	 * @param hostManager
	 * @param hostStatus
	 * @return
	 */
	private boolean isStressed(HostManagerBroadcast hostManager) {
		double util = getAvgCpuUtil(hostManager);

		if (util > upper) {
			return true;
		}
		return false;
	}
	
	/**
	 * Determine if the host is 'underutilized'
	 * 
	 * @param hostManager
	 * @param hostStatus
	 * @return
	 */
	private boolean isUnderUtilized(HostManagerBroadcast hostManager, HostStatus hostStatus) {
		if (getAvgCpuUtil(hostManager) < lower) {
			return true;
		}
		return false;
	}
	

	private double getAvgCpuUtil(HostManagerBroadcast hostManager) {
		double avgCpu = 0;
			
		for (HostStatus status : hostManager.getHistory()) {
			avgCpu += status.getResourcesInUse().getCpu() / hostManager.getHost().getTotalCpu();
		}
		avgCpu = avgCpu / hostManager.getHistory().size();

		return avgCpu;
	}
	
	private double getAvgCpu(HostManagerBroadcast hostManager) {
		double avgCpu = 0;
			
		for (HostStatus status : hostManager.getHistory()) {
			avgCpu += status.getResourcesInUse().getCpu();
		}
		avgCpu = avgCpu / hostManager.getHistory().size();

		return avgCpu;
	}
	
	private double getCpuUtil(HostManagerBroadcast hostManager) {
		if (hostManager.getHistory().size() > 0) {
			return hostManager.getHistory().get(0).getResourcesInUse().getCpu() / hostManager.getHost().getTotalCpu();
		}
		
		return 0;
	}
	
	/**
	 * Order VMs for eviction, in the case this host is 'stressed'
	 * 
	 * @param vms
	 * @param host
	 * @return
	 */
	private ArrayList<VmStatus> orderVmsStressed(ArrayList<VmStatus> vms, Host host) {
		ArrayList<VmStatus> sorted = new ArrayList<VmStatus>();
		
		// Remove VMs with less CPU load than the CPU load by which the
		// host is stressed.
		double cpuExcess = host.getResourceManager().getCpuInUse() - host.getTotalCpu() * upper;
		for (VmStatus vm : vms)
			if (vm.getResourcesInUse().getCpu() >= cpuExcess)
				sorted.add(vm);
		
		if (!sorted.isEmpty())
			// Sort VMs in increasing order by CPU load.
			Collections.sort(sorted, VmStatusComparator.getComparator(VmStatusComparator.CPU_IN_USE));
		else {
			// Add original list of VMs and sort them in decreasing order by 
			// CPU load, so as to avoid trying to migrate the smallest VMs 
			// first (which would not help resolve the stress situation).
			sorted.addAll(vms);
			Collections.sort(sorted, VmStatusComparator.getComparator(VmStatusComparator.CPU_IN_USE));
			Collections.reverse(sorted);
		}
		
		return sorted;
	}
	
	/**
	 * Order VMs for evict, in the case this host is 'underutilized'
	 * 
	 * @param vms
	 * @return
	 */
	private ArrayList<VmStatus> orderVmsUnderUtilized(ArrayList<VmStatus> vms) {
		ArrayList<VmStatus> sources = new ArrayList<VmStatus>(vms);
		
		// Sort VMs in decreasing order by <overall capacity, CPU load>.
		// (Note: since CPU can be oversubscribed, but memory can't, memory 
		// takes priority over CPU when comparing VMs by _size_ (capacity).)
		Collections.sort(sources, VmStatusComparator.getComparator(
				VmStatusComparator.MEMORY, 
				VmStatusComparator.CPU_CORES, 
				VmStatusComparator.CORE_CAP, 
				VmStatusComparator.CPU_IN_USE));
		Collections.reverse(sources);
		
		return sources;
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
	
	private long getActiveHostCount(HostManagerBroadcast hostManager) {
		return hostManager.getGroupSize() - hostManager.getPoweredOffHosts().size();
	}
	
	@Override
	public void onInstall() {

	}

	@Override
	public void onManagerStart() {
		//send power on message
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		hostManager.setManagementState(ManagementState.NORMAL);
		hostManager.getHistory().clear(); //clear monitoring history
		hostManager.setLastShutdownEvent(simulation.getSimulationTime());
		
		simulation.sendEvent(new HostPowerOnEvent(hostManager.getBroadcastingGroup(), hostManager.getHost()));
	}

	@Override
	public void onManagerStop() {

	}

}

