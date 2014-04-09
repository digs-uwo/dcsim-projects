package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collection;

import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.host.Rack.RackState;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.events.VmPlacementEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.*;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.*;
import edu.uwo.csd.dcsim.projects.hierarchical.events.*;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;

/**
 * 
 * 
 * @author Gaston Keller
 *
 */
public class AppPlacementPolicyLevel2 extends Policy {

	protected AutonomicManager target;
	
	/**
	 * Creates an instance of AppPlacementPolicyLevel2.
	 */
	public AppPlacementPolicyLevel2(AutonomicManager target) {
		addRequiredCapability(RackPoolManager.class);
		
		this.target = target;
	}
	
	/**
	 * Note: This event can only come from the DC Manager.
	 */
	public void execute(PlacementRequestEvent event) {
		this.processRequest(event);
	}
	
	/**
	 * Note: This event can only come from Racks in this Cluster in response to placement requests sent by the ClusterManager.
	 */
	public void execute(PlacementRejectEvent event) {
		// Mark sender's status as invalid (to avoid choosing sender again in the next step).
		Collection<RackData> racks = manager.getCapability(RackPoolManager.class).getRacks();
		for (RackData rack : racks) {
			if (rack.getId() == event.getSender()) {
				rack.invalidateStatus(simulation.getSimulationTime());
				break;
			}
		}
		
		// Search again for a placement target.
		this.processRequest(event.getPlacementRequest());
	}
	
	protected void processRequest(PlacementRequestEvent event) {
		RackData targetRack = null;
		
		ConstrainedAppAllocationRequest request = event.getRequest();
		
		Collection<RackData> racks = manager.getCapability(RackPoolManager.class).getRacks();
		
		// Create sublist of active Racks (includes Racks with currently Invalid Status).
		ArrayList<RackData> active = this.getActiveRacksSublist(racks);
		
		// If there are no active Racks, activate one.
		if (active.size() == 0) {
			targetRack = this.getInactiveRack(racks);
		}
		// If there is only one active Rack (and its status is valid), check if the Rack can host 
		// the VM; otherwise, activate a new Rack.
		else if (active.size() == 1) {
			RackData rack = active.get(0);
			if (rack.isStatusValid() && this.canHost(request, rack)) {
				targetRack = rack;
			}
			else {
				targetRack = this.getInactiveRack(racks);
			}
		}
		else {
			// Search for a target Rack among the subset of active Racks.
			
		}
	}
	
	/**
	 * 
	 */
	protected void searchForVmPlacementTarget(VmAllocationRequest request) {
		RackPoolManager rackPool = manager.getCapability(RackPoolManager.class);
		ArrayList<RackData> racks = new ArrayList<RackData>(rackPool.getRacks());
		
		RackData targetRack = null;
		
		// Create sublist of active Racks (includes Racks with currently Invalid Status).
		ArrayList<RackData> active = this.getActiveRacksSublist(racks);
		
		// If there are no active Racks, activate one.
		if (active.size() == 0) {
			targetRack = this.getInactiveRack(racks);
		}
		// If there is only one active Rack (and its status is valid), check if the Rack can host 
		// the VM; otherwise, activate a new Rack.
		else if (active.size() == 1) {
			RackData rack = active.get(0);
			if (rack.isStatusValid() && this.canHost(request, rack)) {
				targetRack = rack;
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
			if (null != maxSpareCapacityRack && this.hasEnoughCapacity(request, maxSpareCapacityRack)) {
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
			ArrayList<VmAllocationRequest> requests = new ArrayList<VmAllocationRequest>();
			requests.add(request);
			simulation.sendEvent(new VmPlacementEvent(targetRack.getRackManager(), requests));
			
			// Invalidate target Rack's status, as we know it to be incorrect until the next status update arrives.
			targetRack.invalidateStatus(simulation.getSimulationTime());
			
			// Mark Rack as active (if it was previously inactive).
			targetRack.activateRack();
		}
		// Could not find suitable target Rack in the Cluster.
		else {
			int clusterId = manager.getCapability(ClusterManager.class).getCluster().getId();
			
			// Contact DC Manager. Reject migration request.
			simulation.sendEvent(new VmPlacementRejectEvent(target, request, clusterId));
		}
	}
	
	protected boolean canHost(ConstrainedAppAllocationRequest request, RackData rack) {
		
		// Get Rack status vector.
		RackStatus status = rack.getCurrentStatus();
		Resources[] vmVector = status.getVmVector();
		int[] spareCapacity = status.getSpareCapacityVector();
		int iActive = status.getActiveHostsIndex();
		int iSuspended = status.getSuspendedHostsIndex();
		int iPoweredOff = status.getPoweredOffHostsIndex();
		
		// Affinity sets
		for (ArrayList<VmAllocationRequest> affinitySet : request.getAffinityVms()) {
			
			// Calculate total resource needs of the VMs in the affinity set.
			int totalCpu = 0;
			int totalMemory = 0;
			int totalBandwidth = 0;
			int totalStorage = 0;
			for (VmAllocationRequest req : affinitySet) {
				totalCpu += req.getCpu();
				totalMemory += req.getMemory();
				totalBandwidth += req.getBandwidth();
				totalStorage += req.getStorage();
			}
			Resources totalReqResources = new Resources(totalCpu, totalMemory, totalBandwidth, totalStorage);
			
			// Check if a currently active Host has enough spare capacity to host the set.
			boolean found = false;
			for (int i = vmVector.length - 1; i >= 0; i--) {
				if (this.theresEnoughCapacity(totalReqResources, vmVector[i])) {
					if (spareCapacity[i] > 0) {
						spareCapacity[i]--;
						found = true;
						
						// TODO: UPDATE STATUS VECTOR
						
						break;
					}
				}
				else
					break;
			}
			
			if (!found) {	// Activate a new Host.
				if (spareCapacity[iSuspended] > 0 || spareCapacity[iPoweredOff] > 0) {
					Resources hostCapacity = rack.getRackDescription().getHostDescription().getResourceCapacity();
					if (this.theresEnoughCapacity(totalReqResources, hostCapacity)) {
						if (spareCapacity[iSuspended] > 0)
							spareCapacity[iSuspended]--;
						else
							spareCapacity[iPoweredOff]--;
						found = true;
						
						// TODO: UPDATE STATUS VECTOR
						
					}
					else
						return false;
				}
				else
					return false;
			}
		}
		
		// Anti-affinity sets
		for (ArrayList<VmAllocationRequest> antiAffinitySet : request.getAntiAffinityVms()) {
			
			// Note: All VMs in the set have equal size and MUST be placed in different Hosts each.
			
			if (antiAffinitySet.size() == 0)	// Checking that the set is not empty -- which should never occur, but...
				continue;
			Resources vmSize = antiAffinitySet.get(0).getResources();
			int nVms = antiAffinitySet.size();
			
			// for each VM, see if there's a Host that can take it; modify vector accordingly
			for (int i = 0; i < vmVector.length; i++) {
				
				if (this.theresEnoughCapacity(vmSize, vmVector[i])) {
					int hosts = Math.min(nVms, spareCapacity[i]);
					
					// TODO: UPDATE STATUS VECTOR (hosts)
					
					nVms -= hosts;
					spareCapacity[i] -= hosts;
				}
				if (nVms == 0)	// All VMs were accounted for.
					break;
			}
			
			// If there still are VMs to account for, active suspended Hosts.
			if (nVms > 0 && spareCapacity[iSuspended] > 0) {
				int hosts = Math.min(nVms, spareCapacity[iSuspended]);
				
				// TODO: UPDATE STATUS VECTOR (hosts)
				
				nVms -= hosts;
				spareCapacity[iSuspended] -= hosts;
			}
			
			// If there still are VMs to account for, active powered-off Hosts.
			if (nVms > 0 && spareCapacity[iPoweredOff] > 0) {
				int hosts = Math.min(nVms, spareCapacity[iPoweredOff]);
				
				// TODO: UPDATE STATUS VECTOR (hosts)
				
				nVms -= hosts;
				spareCapacity[iPoweredOff] -= hosts;
			}
			
			if (nVms > 0)
				return false;
		}
		
		// Independent set
		for (VmAllocationRequest req : request.getIndependentVms()) {
			// for each VM, see if there's a Host that can take it; modify vector accordingly
			boolean found = false;
			for (int i = 0; i < vmVector.length; i++) {
				if (this.theresEnoughCapacity(req.getResources(), vmVector[i])) {
					found = true;
					spareCapacity[i]--;
					
					// TODO: UPDATE STATUS VECTOR
					
					break;
				}
			}
			
			if (!found) {	// Activate a new Host.
				if (spareCapacity[iSuspended] > 0 || spareCapacity[iPoweredOff] > 0) {
					Resources hostCapacity = rack.getRackDescription().getHostDescription().getResourceCapacity();
					if (this.theresEnoughCapacity(req.getResources(), hostCapacity)) {
						if (spareCapacity[iSuspended] > 0)
							spareCapacity[iSuspended]--;
						else
							spareCapacity[iPoweredOff]--;
						
						// TODO: UPDATE STATUS VECTOR
						
					}
					else
						return false;
				}
				else
					return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Verifies whether the given Rack can meet the resource requirements of the VM, 
	 * considering the Rack's max spare capacity and number of suspended and powered off Hosts.
	 */
	protected boolean canHost(VmAllocationRequest request, RackData rack) {
		// Check is Rack has enough spare capacity or inactive (i.e., empty) Hosts.
		if (this.hasEnoughCapacity(request, rack) || 
				rack.getCurrentStatus().getSuspendedHosts() > 0 || 
				rack.getCurrentStatus().getPoweredOffHosts() > 0)
			return true;
		
		return false;
	}
	
	protected boolean theresEnoughCapacity(Resources requiredResources, Resources availableResources) {
		// Check available resources.
		if (availableResources.getCpu() < requiredResources.getCpu())
			return false;
		if (availableResources.getMemory() < requiredResources.getMemory())
			return false;
		if (availableResources.getBandwidth() < requiredResources.getBandwidth())
			return false;
		if (availableResources.getStorage() < requiredResources.getStorage())
			return false;
		
		return true;
	}
	
	/**
	 * Verifies whether the given Rack can meet the resource requirements of the VM.
	 */
	protected boolean hasEnoughCapacity(VmAllocationRequest request, RackData rack) {
		// Check Host capabilities (e.g. core count, core capacity).
		HostDescription hostDescription = rack.getRackDescription().getHostDescription();
		if (hostDescription.getCpuCount() * hostDescription.getCoreCount() < request.getVMDescription().getCores())
			return false;
		if (hostDescription.getCoreCapacity() < request.getVMDescription().getCoreCapacity())
			return false;
		
		// Check available resources.
		Resources availableResources = StandardVmSizes.convertCapacityToResources(rack.getCurrentStatus().getMaxSpareCapacity());
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
	protected ArrayList<RackData> getActiveRacksSublist(Collection<RackData> racks) {
		ArrayList<RackData> active = new ArrayList<RackData>();
		for (RackData rack : racks) {
			if (rack.isRackActive())
				active.add(rack);
		}
		
		return active;
	}
	
	/**
	 * Returns the first inactive Rack found in the given list.
	 * 
	 * This method may return NULL if the list contains no inactive Racks.
	 */
	protected RackData getInactiveRack(Collection<RackData> racks) {
		for (RackData rack : racks) {
			if (!rack.isRackActive())
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
