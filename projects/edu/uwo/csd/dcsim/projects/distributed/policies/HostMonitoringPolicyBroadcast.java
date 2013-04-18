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
import edu.uwo.csd.dcsim.projects.distributed.capabilities.*;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.HostManagerBroadcast.ManagementState;
import edu.uwo.csd.dcsim.projects.distributed.events.*;

public class HostMonitoringPolicyBroadcast extends Policy {

	private static final int EVICTION_TIMEOUT = 2; //the number of rounds to wait to evict a VM
	
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
		hostManager.addHistoryStatus(hostStatus, 5);
		
		//if a migration is pending, wait until it is complete
		if ((hostStatus.getIncomingMigrationCount() > 0) || (hostStatus.getOutgoingMigrationCount() > 0)) return;
		
		
		/*
		 * EVICTING
		 * 
		 * If evicting a VM, wait until the eviction counter runs out.
		 */
		if (hostManager.isEvicting()) {
			
			//check for accepts
			if (hostManager.getVmAccepts().size() > 0) {
				
				//double check that the VM is still running on the host (could have terminated)
				if (hostManager.getHost().getVMAllocation(hostManager.getEvictingVm().getId()) == null) {
					
					//clear eviction state
					hostManager.setEvicting(0);
					hostManager.setEvictingVm(null);
					
				} else {

					//accept the request from the host with the highest utilization
					AcceptVmEvent target = null;
					double targetUtil = -1;
					for (AcceptVmEvent acceptEvent : hostManager.getVmAccepts()) {
						double util = acceptEvent.getHostStatus().getResourcesInUse().getCpu() / acceptEvent.getHost().getTotalCpu();
						if (util > targetUtil)
							target = acceptEvent;
					}
					if (target != null) {
						//trigger migration
						MigrationAction migAction = new MigrationAction(manager, hostManager.getHost(), target.getHost(), target.getVm().getId());
						migAction.execute(simulation, this);			
						
						//clear eviction state
						hostManager.setEvicting(0);
						hostManager.setEvictingVm(null);
					}
				
				}
				//clear VM accepts
				hostManager.getVmAccepts().clear();
				
			} else {
				//decrement evicting counter
				hostManager.setEvicting(hostManager.getEvicting() - 1);
				
				//resend advertise message
				simulation.sendEvent(new AdvertiseVmEvent(hostManager.getBroadcastingGroup(), hostManager.getEvictingVm(), manager));
			}
		}
			
		/*
		 * SHUTTING DOWN 
		 * 
		 * Evict all VMs and shut down, or cancel shut down if cannot evict a VM
		 * 
		 */
		else if (hostManager.getManagementState() == ManagementState.SHUTTING_DOWN) {
			//if shutting down
			
			//check for failed eviction, if failed, cancel shut down
			if (hostManager.getEvictingVm() != null) {
				hostManager.setEvictingVm(null);
				hostManager.setManagementState(ManagementState.NORMAL);
			}
			
			//if evictions complete, shut down
			if (hostStatus.getVms().size() != 0) {
				
				//sort VMs
				ArrayList<VmStatus> vmList = orderVmsUnderUtilized(hostStatus.getVms());
				
				//evict first VM in list
				if (!vmList.isEmpty()) {
					evict(hostManager, vmList.get(0));	
				}
				
			} else {
			
				//send shutdown message
				simulation.sendEvent(new HostShuttingDownEvent(hostManager.getBroadcastingGroup(), hostManager.getHost()));
				
				//shut down
				ShutdownHostAction shutdownAction = new ShutdownHostAction(hostManager.getHost());
				shutdownAction.execute(simulation, this);
				
			}
			
		} 
		
		/*
		 * STRESSED (detect stressed state)
		 * 
		 * Trigger a VM eviction
		 *  
		 */
		else if (isStressed(hostManager)) {
			//host is stressed, evict a VM
			
			//check for failed eviction 
				//if failed, either try another VM or boot new host and retry eviction
			if (hostManager.getEvictingVm() != null) {
				hostManager.setEvictingVm(null);
				
				//boot new host
				Host poweredOffHost = hostManager.getPoweredOffHosts().get(simulation.getRandom().nextInt(hostManager.getPoweredOffHosts().size()));
				simulation.sendEvent(new PowerStateEvent(poweredOffHost, PowerState.POWER_ON));
				
				CountMetric.getMetric(simulation, VmPlacementPolicyBroadcast.HOST_POWER_ON_METRIC + "-" + this.getClass().getSimpleName()).incrementCount();
			}
			
			//sort VMs
			ArrayList<VmStatus> vmList = orderVmsStressed(hostStatus.getVms(), hostManager.getHost());
			
			//evict first VM in list
			if (!vmList.isEmpty()) {
				evict(hostManager, vmList.get(0));	
			}
			
		} 
		
		/*
		 * UNDERUTILIZED (detect underutilized state)
		 *
		 * Decide whether or not to switch into SHUTDOWN
		 */
		
		else if (isUnderUtilized(hostManager, hostStatus)) {
			//host is underutilized, decide if should switch to eviction, if so, evict a VM
			if (simulation.getRandom().nextDouble() < 0.01) {
				hostManager.setManagementState(ManagementState.SHUTTING_DOWN); 
				
				//sort VMs
				ArrayList<VmStatus> vmList = orderVmsUnderUtilized(hostStatus.getVms());
				
				//evict first VM in list
				if (!vmList.isEmpty()) {
					evict(hostManager, vmList.get(0));	
				}
			}

		} 
		
		/*
		 * NORMAL STATE (no evictions, not shutting down, not stressed, no current migrations... note the host COULD be underutilized, though)
		 * 
		 * Check for received VM advertisements, respond if possible
		 * 
		 */
		if (!isStressed(hostManager) && 
				!(hostManager.getManagementState() == ManagementState.SHUTTING_DOWN) &&
				!hostManager.isEvicting() &&
				!(hostStatus.getIncomingMigrationCount() > 0) &&
				!(hostStatus.getOutgoingMigrationCount() > 0)) {
			
			//check for received VM advertisements
			for (AdvertiseVmEvent ad : hostManager.getVmAdvertisements()) {
				Host host = hostManager.getHost();
				VmStatus vm = ad.getVm();
				
				//check if we are capable of hosting this VM
				if (canHost(host, hostStatus, vm) &&
						(hostStatus.getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / host.getResourceManager().getTotalCpu() <= target) {

					//send accept message
					simulation.sendEvent(new AcceptVmEvent(ad.getHostManager(), vm, host, manager, hostStatus));

					//only accept one VM... TODO change this? (will have to modify HostStatus... not a problem)
					break;
				}

			}
		}
		
		//clear VM advertisements
		hostManager.getVmAdvertisements().clear();
	}
	
	/**
	 * Evict a single VM from this host
	 * 
	 * @param hostManager
	 * @param vm
	 */
	private void evict(HostManagerBroadcast hostManager, VmStatus vm) {
		//setup eviction counter/data
		hostManager.setEvicting(EVICTION_TIMEOUT);
		hostManager.setEvictingVm(vm);
				
		//send advertise message
		simulation.sendEvent(new AdvertiseVmEvent(hostManager.getBroadcastingGroup(), vm, manager));		
	}
	
	/**
	 * Determine if the host is 'stressed'
	 * 
	 * @param hostManager
	 * @param hostStatus
	 * @return
	 */
	private boolean isStressed(HostManagerBroadcast hostManager) {
		if (getAvgCpuUtil(hostManager) > upper) {
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
			avgCpu += status.getResourcesInUse().getCpu();
		}
		avgCpu = avgCpu / hostManager.getHistory().size();
		avgCpu = avgCpu / hostManager.getHost().getTotalCpu();
		
		return avgCpu;
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
	 * Receive a VM advertisement message
	 * 
	 * @param event
	 */
	public void execute(AdvertiseVmEvent event) {
		//received a VM advertisement
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		//check if event is from this host
		if (event.getHostManager() != manager)
			hostManager.getVmAdvertisements().add(event);
	}
	
	/**
	 * Receive a VM accept message
	 * 
	 * @param event
	 */
	public void execute(AcceptVmEvent event) {
		//a Host has accepted your advertised VM
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		//ensure that this VM is still being advertised
		if (hostManager.isEvicting() && hostManager.getEvictingVm().equals(event.getVm())) {
			
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
	
	@Override
	public void onInstall() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStart() {
		//send power on message
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		hostManager.setManagementState(ManagementState.NORMAL);
		hostManager.getHistory().clear(); //clear monitoring history
		
		simulation.sendEvent(new HostPowerOnEvent(hostManager.getBroadcastingGroup(), hostManager.getHost()));
	}

	@Override
	public void onManagerStop() {

	}

}
