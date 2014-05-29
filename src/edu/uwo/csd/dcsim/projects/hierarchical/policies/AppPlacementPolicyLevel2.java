package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collection;

import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.projects.hierarchical.ConstrainedAppAllocationRequest;
import edu.uwo.csd.dcsim.projects.hierarchical.RackData;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.ClusterManager;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.RackPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.events.PlacementRejectEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.PlacementRequestEvent;

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
		
		simulation.getLogger().debug(String.format("[Cluster #%d] AppPlacementPolicyLevel2 - New Placement reject - App #%d.",
				manager.getCapability(ClusterManager.class).getCluster().getId(),
				event.getRequest().getId()));
		
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
		
		simulation.getLogger().debug(String.format("[Cluster #%d] AppPlacementPolicyLevel2.processRequest() - App #%d.",
				manager.getCapability(ClusterManager.class).getCluster().getId(),
				request.getId()));
		
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
			if (rack.isStatusValid() && RackData.canHost(request, rack.getCurrentStatus().getStatusVector(), rack.getRackDescription())) {
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
				int hostActivations = RackData.calculateMinHostActivations(request, rack.getCurrentStatus().getStatusVector(), rack.getRackDescription().getHostDescription());
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
			
			// If we have not found a target Rack among the subset of active Racks, activate a new Rack.
			if (null == targetRack && active.size() < racks.size()) {
				targetRack = this.getInactiveRack(racks);
			}
		}
		
		if (null != targetRack) {
			
			simulation.getLogger().debug(String.format("[Cluster #%d] AppPlacementPolicyLevel2.processRequest() - App #%d - Found placement target: Rack #%d.",
					manager.getCapability(ClusterManager.class).getCluster().getId(),
					request.getId(),
					targetRack.getId()));
			
			// Found target. Send placement request.
			simulation.sendEvent(new PlacementRequestEvent(targetRack.getRackManager(), request));
			
			// Invalidate target Rack's status, as we know it to be incorrect until the next status update arrives.
			targetRack.invalidateStatus(simulation.getSimulationTime());
			
			// Mark Rack as active (in case it was previously inactive).
			targetRack.activateRack();
		}
		// Could not find suitable target Rack in the Cluster.
		else {
			
			simulation.getLogger().debug(String.format("[Cluster #%d] AppPlacementPolicyLevel2.processRequest() - App #%d - Failed to find placement target.",
					manager.getCapability(ClusterManager.class).getCluster().getId(),
					request.getId()));
			
			int clusterId = manager.getCapability(ClusterManager.class).getCluster().getId();
			
			// Contact DC Manager. Reject migration request.
			simulation.sendEvent(new PlacementRejectEvent(target, request, clusterId));
		}
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
		// Auto-generated method stub
	}
	
	@Override
	public void onManagerStart() {
		// Auto-generated method stub
	}
	
	@Override
	public void onManagerStop() {
		// Auto-generated method stub
	}

}
