package edu.uwo.csd.dcsim.projects.distributed.policies;

import java.util.ArrayList;
import java.util.Collections;

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
	
	//executes on a regular interval to check host status, and take possible action
	public void execute() {
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		HostStatus hostStatus = new HostStatus(hostManager.getHost(), simulation.getSimulationTime());
		hostManager.addHistoryStatus(hostStatus, 5);
		
		//if evicting, wait until eviction counter runs out
		if (hostManager.isEvicting()) {
			//decrement evicting counter
			hostManager.setEvicting(hostManager.getEvicting() - 1);
			
			//resend advertise message
			simulation.sendEvent(new AdvertiseVmEvent(hostManager.getBroadcastingGroup(), hostManager.getEvictingVm(), manager));
			
		} else if (hostManager.getManagementState() == ManagementState.SHUTTING_DOWN) {
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
			
		} else if (isStressed(hostManager, hostStatus)) {
			//host is stressed, evict a VM
			
			//check for failed eviction 
				//if failed, either try another VM or boot new host and retry eviction
			if (hostManager.getEvictingVm() != null) {
				hostManager.setEvictingVm(null);
				
				//boot new host
				Host poweredOffHost = hostManager.getPoweredOffHosts().get(simulation.getRandom().nextInt(hostManager.getPoweredOffHosts().size()));
				simulation.sendEvent(new PowerStateEvent(poweredOffHost, PowerState.POWER_ON));
			}
			
			//sort VMs
			ArrayList<VmStatus> vmList = orderVmsStressed(hostStatus.getVms(), hostManager.getHost());
			
			//evict first VM in list
			if (!vmList.isEmpty()) {
				evict(hostManager, vmList.get(0));	
			}
			
		} else if (isUnderUtilized(hostManager, hostStatus)) {
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
		
		if (!isStressed(hostManager, hostStatus) && 
				!(hostManager.getManagementState() == ManagementState.SHUTTING_DOWN) &&
				!hostManager.isEvicting()) {
			
			//check for received VM advertisements
			for (AdvertiseVmEvent ad : hostManager.getVmAdvertisements()) {
				Host host = hostManager.getHost();
				VmStatus vm = ad.getVm();
				
				//check if we are capable of hosting this VM
				if (canHost(host, hostStatus, vm) &&
						(hostStatus.getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / host.getResourceManager().getTotalCpu() <= target) {

					//send accept message
					simulation.sendEvent(new AcceptVmEvent(ad.getHostManager(), vm, host, manager));

					//only accept one VM... TODO change this? (will have to modify HostStatus... not a problem)
					break;
				}

			}
		}
		
		//clear VM advertisements
		hostManager.getVmAdvertisements().clear();
	}
	
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
	
	private void evict(HostManagerBroadcast hostManager, VmStatus vm) {
		//setup eviction counter/data
		hostManager.setEvicting(EVICTION_TIMEOUT);
		hostManager.setEvictingVm(vm);
				
		//send advertise message
		simulation.sendEvent(new AdvertiseVmEvent(hostManager.getBroadcastingGroup(), vm, manager));		
	}
	
	private boolean isStressed(HostManagerBroadcast hostManager, HostStatus hostStatus) {
		//TODO change to use average over history
		if (hostManager.getHost().getResourceManager().getCpuInUse() / hostManager.getHost().getTotalCpu() > upper) {
			return true;
		}
		return false;
	}
	
	private boolean isUnderUtilized(HostManagerBroadcast hostManager, HostStatus hostStatus) {
		//TODO change to use average over history
		if (hostManager.getHost().getResourceManager().getCpuInUse() / hostManager.getHost().getTotalCpu() < lower) {
			return true;
		}
		return false;
	}
	
	public void execute(AdvertiseVmEvent event) {
		//received a VM advertisement
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		//check if event is from this host
		if (event.getHostManager() != manager)
			hostManager.getVmAdvertisements().add(event);
	}
	
	public void execute(AcceptVmEvent event) {
		//a Host has accepted your advertised VM
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		//ensure that this VM is still being advertised
		if (hostManager.isEvicting() && hostManager.getEvictingVm().equals(event.getVm())) {
			
			//trigger migration
			MigrationAction migAction = new MigrationAction(manager, hostManager.getHost(), event.getHost(), event.getVm().getId());
			migAction.execute(simulation, this);			
			
			//clear eviction state
			hostManager.setEvicting(0);
			hostManager.setEvictingVm(null);
		}

	}
	
	public void execute(HostShuttingDownEvent event) {
		//store in list of powered off hosts
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		if (event.getHost() != hostManager.getHost())
			hostManager.getPoweredOffHosts().add(event.getHost());
	}
	
	public void execute(HostPowerOnEvent event) {
		//remove from list of powered off hosts
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		hostManager.getPoweredOffHosts().remove(event.getHost());
	}
	
	@Override
	public void onInstall() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStart() {
		//send power on message
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		simulation.sendEvent(new HostPowerOnEvent(hostManager.getBroadcastingGroup(), hostManager.getHost()));
	}

	@Override
	public void onManagerStop() {
		
	}

}
