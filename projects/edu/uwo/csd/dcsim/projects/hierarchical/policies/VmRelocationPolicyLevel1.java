package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.Collection;

import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.projects.hierarchical.*;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.*;
import edu.uwo.csd.dcsim.projects.hierarchical.events.*;

/**
 * 
 * 
 * @author Gaston Keller
 *
 */
public abstract class VmRelocationPolicyLevel1 extends Policy {

	protected AutonomicManager target;
	
	/**
	 * Creates an instance of VmRelocationPolicyLevel1.
	 */
	public VmRelocationPolicyLevel1(AutonomicManager target) {
		addRequiredCapability(RackPoolManager.class);
		addRequiredCapability(MigRequestRecord.class);
		
		this.target = target;
	}
	
	/**
	 * This event can come from a Rack in this Cluster or from the DC Manager.
	 */
	public void execute(MigRequestEvent event) {
		MigRequestEntry entry = new MigRequestEntry(event.getVm(), event.getOrigin(), event.getSender());
		
		// Store info about migration request just received.
		manager.getCapability(MigRequestRecord.class).addEntry(entry);
		
		this.searchForVmMigrationTarget(entry);
	}
	
	/**
	 * This event can only come from Racks in this Cluster in response to migration requests sent by the ClusterManager.
	 */
	public void execute(MigRejectEvent event) {
		// Mark sender's status as invalid (to avoid choosing sender again in the next step).
		Collection<RackData> racks = manager.getCapability(RackPoolManager.class).getRacks();
		for (RackData rack : racks) {
			if (rack.getId() == event.getSender()) {
				rack.invalidateStatus(simulation.getSimulationTime());
				break;
			}
		}
		
		// Get entry from record and search again for a migration target.
		MigRequestEntry entry = manager.getCapability(MigRequestRecord.class).getEntry(event.getVm(), event.getOrigin());
		this.searchForVmMigrationTarget(entry);
	}
	
	/**
	 * 
	 */
	protected void searchForVmMigrationTarget(MigRequestEntry entry) {
		RackPoolManager rackPool = manager.getCapability(RackPoolManager.class);
		Collection<RackData> racks = rackPool.getRacks();
		
		double maxSpareCapacity = 0;
		RackData maxSpareCapacityRack = null;
		int minInactiveHosts = Integer.MAX_VALUE;
		RackData mostLoadedWithSuspended = null;
		RackData mostLoadedWithPoweredOff = null;
		for (RackData rack : racks) {
			// Filter out Racks with a currently invalid status.
			// If the Rack sending the request belongs in this Cluster, skip it, too.
			if (!rack.isStatusValid() || rack.getId() == entry.getSender())
				continue;
			
			RackStatus status = rack.getCurrentStatus();
			
			// Find the Rack with the most spare capacity.
			if (status.getMaxSpareCapacity() > maxSpareCapacity) {
				maxSpareCapacity = status.getMaxSpareCapacity();
				maxSpareCapacityRack = rack;
			}
			
			// Find the most loaded Racks (i.e., the Racks with the smallest number of inactive 
			// Hosts) that have at least one suspended or powered off Host.
			int inactiveHosts = status.getSuspendedHosts() + status.getPoweredOffHosts();
			if (inactiveHosts > 0 && inactiveHosts < minInactiveHosts) {
				minInactiveHosts = inactiveHosts;
				if (status.getSuspendedHosts() > 0)
					mostLoadedWithSuspended = rack;
				if (status.getPoweredOffHosts() > 0)
					mostLoadedWithPoweredOff = rack;
			}
		}
		
		RackData targetRack = null;
		
		// Check if Rack with most spare capacity has enough resources to take the VM (i.e., become target).
		if (null != maxSpareCapacityRack && this.canHost(entry.getVm(), maxSpareCapacityRack)) {
			targetRack = maxSpareCapacityRack;
		}
		// Otherwise, make the most loaded Rack with a suspended Host the target.
		else if (null != mostLoadedWithSuspended) {
			targetRack = mostLoadedWithSuspended;
		}
		// Last recourse: make the most loaded Rack with a powered off Host the target.
		else if (null != mostLoadedWithPoweredOff) {
			targetRack = mostLoadedWithPoweredOff;
		}
		
		if (null != targetRack) {
			// Found target. Send migration request.
			simulation.sendEvent(new MigRequestEvent(targetRack.getRackManager(), entry.getVm(), entry.getOrigin(), 0));
		}
		// Could not find suitable target Rack in the Cluster.
		else {
			int clusterId = manager.getCapability(ClusterManager.class).getCluster().getId();
			
			// If event's sender belongs in this Cluster, request assistance from DC Manager 
			// to find a target Host for the VM migration in another Cluster.
			if (null != rackPool.getRack(entry.getSender())) {
				simulation.sendEvent(new MigRequestEvent(target, entry.getVm(), entry.getOrigin(), clusterId));
			}
			// Event's sender does not belong in this Cluster.
			else {
				// Migration request was sent by DC Manager. Reject migration request.
				simulation.sendEvent(new MigRejectEvent(target, entry.getVm(), entry.getOrigin(), clusterId));
			}
			
			// In any case, delete entry from migration requests record.
			// If requested assistance from DC Manager, I'm not seeing this request again.
			// If rejected the request, I'm not seeing this request again.
			manager.getCapability(MigRequestRecord.class).removeEntry(entry);
		}
	}
	
	/**
	 * 
	 */
	protected boolean canHost(VmStatus vm, RackData rack) {
		// Check Host capabilities (e.g. core count, core capacity).
		HostDescription hostDescription = rack.getRackDescription().getHostDescription();
		if (hostDescription.getCpuCount() * hostDescription.getCoreCount() < vm.getCores())
			return false;
		if (hostDescription.getCoreCapacity() < vm.getCoreCapacity())
			return false;
		
		// Check available resources.
		Resources availableResources = AverageVmSizes.convertCapacityToResources(rack.getCurrentStatus().getMaxSpareCapacity());
		Resources vmResources = vm.getResourcesInUse();
		if (availableResources.getCpu() < vmResources.getCpu())
			return false;
		if (availableResources.getMemory() < vmResources.getMemory())
			return false;
		if (availableResources.getBandwidth() < vmResources.getBandwidth())
			return false;
		if (availableResources.getStorage() < vmResources.getStorage())
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
