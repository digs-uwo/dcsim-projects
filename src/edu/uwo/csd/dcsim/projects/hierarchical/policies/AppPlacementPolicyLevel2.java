package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collection;

import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.projects.hierarchical.ConstrainedAppAllocationRequest;
import edu.uwo.csd.dcsim.projects.hierarchical.RackData;
import edu.uwo.csd.dcsim.projects.hierarchical.RackStatusVector;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.ClusterManager;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.RackPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.events.PlacementRejectEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.PlacementRequestEvent;
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
		this.processRequest(event.getRequest());
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
		this.processRequest(event.getRequest());
	}
	
	protected void processRequest(ConstrainedAppAllocationRequest request) {
		RackData targetRack = null;
		
		Collection<RackData> racks = manager.getCapability(RackPoolManager.class).getRacks();
		
		// Create sublist of active Racks (includes Racks with currently Invalid Status).
		ArrayList<RackData> active = this.getActiveRacksSublist(racks);
		
		// TODO: SHOULD THIS active LIST CONTAIN ONLY RACKS WITH A VALID STATUS ???
		// AFTER ALL, WE AVOID RACKS W/ INVALID STATUS AT EACH STEP !!!
		
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
			
			int minHostActivations = Integer.MAX_VALUE;
			RackData mostLoaded = null;
			for (RackData rack : active) {
				
				// Filter out Racks with a currently invalid status.
				if (!rack.isStatusValid())
					continue;
				
				// Find the Rack that would result in the least number of Host activations.
				// If several Racks require a minimum number of Host activations,
				// pick the most loaded Rack among them.
				int hostActivations = this.calculateMinHostActivations(request, rack);
				if (hostActivations >= 0) {
					if (hostActivations < minHostActivations) {
						minHostActivations = hostActivations;
						mostLoaded = rack;
					}
					else if (hostActivations == minHostActivations) {
						if (rack.getCurrentStatus().getActiveHosts() > mostLoaded.getCurrentStatus().getActiveHosts())
							mostLoaded = rack;
					}
				}
			}
			targetRack = mostLoaded;
		}
		
		if (null != targetRack) {
			// Found target. Send placement request.
			simulation.sendEvent(new PlacementRequestEvent(targetRack.getRackManager(), request));
			
			// Invalidate target Rack's status, as we know it to be incorrect until the next status update arrives.
			targetRack.invalidateStatus(simulation.getSimulationTime());
			
			// Mark Rack as active (in case it was previously inactive).
			targetRack.activateRack();
		}
		// Could not find suitable target Rack in the Cluster.
		else {
			int clusterId = manager.getCapability(ClusterManager.class).getCluster().getId();
			
			// Contact DC Manager. Reject migration request.
			simulation.sendEvent(new PlacementRejectEvent(target, request, clusterId));
		}
	}
	
	protected boolean canHost(ConstrainedAppAllocationRequest request, RackData rack) {
		if (this.calculateMinHostActivations(request, rack) >= 0)
			return true;
		
		return false;
	}
	
	/**
	 * Verifies whether the given Rack can meet the resource requirements of the application,
	 * based on the Rack's status vector.
	 */
	protected int calculateMinHostActivations(ConstrainedAppAllocationRequest request, RackData rack) {
		int failed = -1;
		
		// TODO: THIS METHOD DOES NOT CHECK THE HW CAPABILITIES OF THE RACK; THAT SHOULD BE DONE AT A HIGHER LEVEL.
		// AT THIS STAGE, WE ASSUME THAT ANY REQUEST THAT COMES THIS WAY WOULD HAVE ITS HW NEEDS MET (I.E., CPU CORES & CORE CAPACITY).
		
		// TODO: THIS METHOD NEEDS TO BE RE-WORKED (MODULARIZED).
		
		// Get Rack status vector.
		RackStatusVector statusVector = rack.getCurrentStatus().getStatusVector().copy();
		
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
			for (int i = statusVector.vmVector.length - 1; i >= 0; i--) {
				if (this.theresEnoughCapacity(totalReqResources, statusVector.vmVector[i])) {
					if (statusVector.vector[i] > 0) {
						found = true;
						statusVector.vector[i]--;
						Resources reminder = statusVector.vmVector[i].subtract(totalReqResources);
						this.updateSpareCapacityVector(statusVector, reminder, 1);
						break;
					}
				}
				else
					break;
			}
			
			if (!found) {	// Activate a new Host.
				if (statusVector.vector[statusVector.iSuspended] > 0 || statusVector.vector[statusVector.iPoweredOff] > 0) {
					Resources hostCapacity = rack.getRackDescription().getHostDescription().getResourceCapacity();
					if (this.theresEnoughCapacity(totalReqResources, hostCapacity)) {
						found = true;
						if (statusVector.vector[statusVector.iSuspended] > 0)
							statusVector.vector[statusVector.iSuspended]--;
						else
							statusVector.vector[statusVector.iPoweredOff]--;
						statusVector.vector[statusVector.iActive]++;
						Resources reminder = hostCapacity.subtract(totalReqResources);
						this.updateSpareCapacityVector(statusVector, reminder, 1);
					}
					else
						return failed;
				}
				else
					return failed;
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
			for (int i = 0; i < statusVector.vmVector.length; i++) {
				
				if (this.theresEnoughCapacity(vmSize, statusVector.vmVector[i])) {
					int hosts = Math.min(nVms, statusVector.vector[i]);
					Resources reminder = statusVector.vmVector[i].subtract(vmSize);
					this.updateSpareCapacityVector(statusVector, reminder, hosts);
					nVms -= hosts;
					statusVector.vector[i] -= hosts;
				}
				if (nVms == 0)	// All VMs were accounted for.
					break;
			}
			
			// If there still are VMs to account for, active suspended Hosts.
			if (nVms > 0 && statusVector.vector[statusVector.iSuspended] > 0) {
				int hosts = Math.min(nVms, statusVector.vector[statusVector.iSuspended]);
				Resources hostCapacity = rack.getRackDescription().getHostDescription().getResourceCapacity();
				Resources reminder = hostCapacity.subtract(vmSize);
				this.updateSpareCapacityVector(statusVector, reminder, hosts);
				nVms -= hosts;
				statusVector.vector[statusVector.iSuspended] -= hosts;
				statusVector.vector[statusVector.iActive] += hosts;
			}
			
			// If there still are VMs to account for, active powered-off Hosts.
			if (nVms > 0 && statusVector.vector[statusVector.iPoweredOff] > 0) {
				int hosts = Math.min(nVms, statusVector.vector[statusVector.iPoweredOff]);
				Resources hostCapacity = rack.getRackDescription().getHostDescription().getResourceCapacity();
				Resources reminder = hostCapacity.subtract(vmSize);
				this.updateSpareCapacityVector(statusVector, reminder, hosts);
				nVms -= hosts;
				statusVector.vector[statusVector.iPoweredOff] -= hosts;
				statusVector.vector[statusVector.iActive] += hosts;
			}
			
			if (nVms > 0)
				return failed;
		}
		
		// Independent set
		for (VmAllocationRequest req : request.getIndependentVms()) {
			// for each VM, see if there's a Host that can take it; modify vector accordingly
			boolean found = false;
			for (int i = 0; i < statusVector.vmVector.length; i++) {
				if (this.theresEnoughCapacity(req.getResources(), statusVector.vmVector[i])) {
					found = true;
					statusVector.vector[i]--;
					Resources reminder = statusVector.vmVector[i].subtract(req.getResources());
					this.updateSpareCapacityVector(statusVector, reminder, 1);
					break;
				}
			}
			
			if (!found) {	// Activate a new Host.
				if (statusVector.vector[statusVector.iSuspended] > 0 || statusVector.vector[statusVector.iPoweredOff] > 0) {
					Resources hostCapacity = rack.getRackDescription().getHostDescription().getResourceCapacity();
					if (this.theresEnoughCapacity(req.getResources(), hostCapacity)) {
						if (statusVector.vector[statusVector.iSuspended] > 0)
							statusVector.vector[statusVector.iSuspended]--;
						else
							statusVector.vector[statusVector.iPoweredOff]--;
						statusVector.vector[statusVector.iActive]++;
						Resources reminder = hostCapacity.subtract(req.getResources());
						this.updateSpareCapacityVector(statusVector, reminder, 1);
					}
					else
						return failed;
				}
				else
					return failed;
			}
		}
		
		RackStatusVector original = rack.getCurrentStatus().getStatusVector();
		return statusVector.vector[statusVector.iActive] - original.vector[original.iActive];
	}
	
	protected void updateSpareCapacityVector(RackStatusVector statusVector, Resources reminder, int count) {
		
		for (int i = statusVector.vmVector.length - 1; i >= 0; i--) {
			if (this.theresEnoughCapacity(statusVector.vmVector[i], reminder)) {
				statusVector.vector[i] += count;
				break;
			}
		}
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
