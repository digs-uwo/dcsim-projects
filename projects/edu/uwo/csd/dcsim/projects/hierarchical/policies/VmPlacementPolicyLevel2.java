package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collection;

import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.events.VmPlacementEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.*;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.*;
import edu.uwo.csd.dcsim.projects.hierarchical.events.*;
import edu.uwo.csd.dcsim.vm.VMAllocationRequest;

/**
 * 
 * 
 * @author Gaston Keller
 *
 */
public class VmPlacementPolicyLevel2 extends Policy {

	protected AutonomicManager target;
	
	/**
	 * Creates an instance of VmPlacementPolicyLevel2.
	 */
	public VmPlacementPolicyLevel2(AutonomicManager target) {
		addRequiredCapability(RackPoolManager.class);
		
		this.target = target;
	}
	
	/**
	 * This event can only come from the DC Manager.
	 */
	public void execute(VmPlacementEvent event) {
		// The event contains a single placement request.
		this.searchForVmPlacementTarget(event.getVMAllocationRequests().get(0));
	}
	
	/**
	 * This event can only come from Racks in this Cluster in response to migration requests sent by the ClusterManager.
	 */
	public void execute(VmPlacementRejectEvent event) {
		// Mark sender's status as invalid (to avoid choosing sender again in the next step).
		Collection<RackData> racks = manager.getCapability(RackPoolManager.class).getRacks();
		for (RackData rack : racks) {
			if (rack.getId() == event.getSender()) {
				rack.invalidateStatus(simulation.getSimulationTime());
				break;
			}
		}
		
		// Search again for a placement target.
		this.searchForVmPlacementTarget(event.getVmAllocationRequest());
	}
	
	/**
	 * 
	 */
	protected void searchForVmPlacementTarget(VMAllocationRequest request) {
		RackPoolManager rackPool = manager.getCapability(RackPoolManager.class);
		ArrayList<RackData> racks = new ArrayList<RackData>(rackPool.getRacks());
		
		RackData targetRack = null;
		
		// Create sublist of active Racks (includes Racks with currently Invalid Status).
		ArrayList<RackData> active = this.getActiveRacksSublist(racks);
		
		// If there are no active Racks, activate one.
		if (active.size() == 0) {
			targetRack = this.getInactiveRack(racks);
		}
		// If there is only one active Rack, check if the Rack can host the VM; otherwise, activate a new Rack.
		else if (active.size() == 1) {
			if (this.canHost(request, active.get(0))) {
				targetRack = active.get(0);
			}
			else {
				targetRack = this.getInactiveRack(racks);
			}
		}
		else {
			// Search for a target Rack among the subset of active Racks.
			
			double maxSpareCapacity = 0;
			RackData maxSpareCapacityRack = null;
			int minInactiveHosts = Integer.MAX_VALUE;
			RackData mostLoadedWithSuspended = null;
			RackData mostLoadedWithPoweredOff = null;
			for (RackData rack : racks) {
				// Filter out Racks with a currently invalid status.
				if (!rack.isStatusValid())
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
			
			// Check if Rack with most spare capacity has enough resources to take the VM (i.e., become target).
			if (null != maxSpareCapacityRack && this.canHost(request, maxSpareCapacityRack)) {
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
			
			// If we have not found a target Rack among the subset of active Racks, activate a new Rack.
			if (null == targetRack && active.size() < racks.size()) {
				targetRack = this.getInactiveRack(racks);
			}
		}
		
		if (null != targetRack) {
			// Found target. Send placement request.
			ArrayList<VMAllocationRequest> requests = new ArrayList<VMAllocationRequest>();
			requests.add(request);
			simulation.sendEvent(new VmPlacementEvent(targetRack.getRackManager(), requests));
			
			// Invalidate target Rack's status, as we know it to be incorrect until the next status update arrives.
			targetRack.invalidateStatus(simulation.getSimulationTime());
		}
		// Could not find suitable target Rack in the Cluster.
		else {
			int clusterId = manager.getCapability(ClusterManager.class).getCluster().getId();
			
			// Contact DC Manager. Reject migration request.
			simulation.sendEvent(new VmPlacementRejectEvent(target, request, clusterId));
		}
	}
	
	/**
	 * Verifies whether the given Rack can meet the resource requirements of the VM.
	 */
	protected boolean canHost(VMAllocationRequest request, RackData rack) {
		// Check Host capabilities (e.g. core count, core capacity).
		HostDescription hostDescription = rack.getRackDescription().getHostDescription();
		if (hostDescription.getCpuCount() * hostDescription.getCoreCount() < request.getVMDescription().getCores())
			return false;
		if (hostDescription.getCoreCapacity() < request.getVMDescription().getCoreCapacity())
			return false;
		
		// Check available resources.
		Resources availableResources = AverageVmSizes.convertCapacityToResources(rack.getCurrentStatus().getMaxSpareCapacity());
		if (availableResources.getCpu() < request.getCpu())
			return false;
		if (availableResources.getMemory() < request.getMemory())
			return false;
		if (availableResources.getBandwidth() < request.getBandwidth())
			return false;
		if (availableResources.getStorage() < request.getStorage())
			return false;
		
		return true;
	}
	
	/**
	 * Returns the subset of active Racks in the given list. The sublist preserves the order of 
	 * the elements in the original list.
	 */
	protected ArrayList<RackData> getActiveRacksSublist(ArrayList<RackData> racks) {
		ArrayList<RackData> active = new ArrayList<RackData>();
		for (RackData rack : racks) {
			if (rack.getCurrentStatus().getActiveHosts() > 0)
				active.add(rack);
		}
		
		return active;
	}
	
	/**
	 * Returns the first inactive Rack found in the given list. A Rack is considered inactive 
	 * if it has no active Hosts. Otherwise, it's consider active.
	 * 
	 * This method may return NULL if the list contains no inactive Racks.
	 */
	protected RackData getInactiveRack(ArrayList<RackData> racks) {
		for (RackData rack : racks) {
			if (rack.getCurrentStatus().getActiveHosts() == 0)
				return rack;
		}
		
		return null;
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
