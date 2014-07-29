package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collection;

import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.projects.hierarchical.MigRequestEntry;
import edu.uwo.csd.dcsim.projects.hierarchical.RackData;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.ClusterManager;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.MigRequestRecord;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.RackPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.events.AppMigRequestEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.AppMigRejectEvent;

/**
 * 
 * 
 * @author Gaston Keller
 *
 */
public class AppRelocationPolicyLevel2 extends Policy {

	protected AutonomicManager target;
	
	/**
	 * Creates an instance of AppRelocationPolicyLevel2.
	 */
	public AppRelocationPolicyLevel2(AutonomicManager target) {
		addRequiredCapability(RackPoolManager.class);
		addRequiredCapability(MigRequestRecord.class);
		
		this.target = target;
	}
	
	/**
	 * This event can come from a Rack in this Cluster or from the DC Manager.
	 */
	public void execute(AppMigRequestEvent event) {
		
		simulation.getLogger().debug(String.format("[Cluster #" + manager.getCapability(ClusterManager.class).getCluster().getId() + "]"
				+ " AppRelocationPolicyLevel2 - New MigRequest - App #" + event.getApplication().getId()));
		
		MigRequestEntry entry = new MigRequestEntry(event.getApplication(), event.getOrigin(), event.getSender());
		
		// Store info about migration request just received.
		manager.getCapability(MigRequestRecord.class).addEntry(entry);
		
		this.searchForAppMigrationTarget(entry);
	}
	
	/**
	 * This event can only come from Racks in this Cluster in response to migration requests sent by the ClusterManager.
	 */
	public void execute(AppMigRejectEvent event) {
		
		simulation.getLogger().debug(String.format("[Cluster #%d] AppRelocationPolicyLevel2 - New Migration reject - App #%d.",
				manager.getCapability(ClusterManager.class).getCluster().getId(),
				event.getApplication().getId()));
		
		// Mark sender's status as invalid (to avoid choosing sender again in the next step).
		Collection<RackData> racks = manager.getCapability(RackPoolManager.class).getRacks();
		for (RackData rack : racks) {
			if (rack.getId() == event.getSender()) {
				rack.invalidateStatus(simulation.getSimulationTime());
				break;
			}
		}
		
		// Get entry from record and search again for a migration target.
		MigRequestEntry entry = manager.getCapability(MigRequestRecord.class).getEntry(event.getApplication(), event.getOrigin());
		this.searchForAppMigrationTarget(entry);
	}
	
	/**
	 * 
	 */
	protected void searchForAppMigrationTarget(MigRequestEntry entry) {
		
		simulation.getLogger().debug(String.format("[Cluster #%d] AppRelocationPolicyLevel2 - Searching for target Rack...",
				manager.getCapability(ClusterManager.class).getCluster().getId()));
		
		RackPoolManager rackPool = manager.getCapability(RackPoolManager.class);
		ArrayList<RackData> racks = new ArrayList<RackData>(rackPool.getRacks());
		
		RackData targetRack = null;
		
		// Create sublist of active Racks (includes Racks with currently Invalid Status).
		ArrayList<RackData> active = this.getActiveRacksSublist(racks);
		
		// If there are no active Racks, activate one.
		if (active.size() == 0) {
			targetRack = this.getInactiveRack(racks);
		}
		// If there is only one active Rack, the Rack is not the sender of the migration request 
		// (i.e., the request came from outside the Cluster), and the Rack's status is valid, 
		// then check if the Rack can host the VM; otherwise, activate a new Rack.
		else if (active.size() == 1) {
			RackData rack = active.get(0);
			if (rack.getId() != entry.getSender() && rack.isStatusValid() && RackData.canHost(entry.getApplication(), rack.getCurrentStatus().getStatusVector(), rack.getRackDescription())) {
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
				// Skip as well the Rack that sent the request -- if it belongs in this Cluster.
				if (!rack.isStatusValid() || rack.getId() == entry.getSender())
					continue;
				
				// Find the Rack that would result in the least number of Host activations.
				// If several Racks require a minimum number of Host activations,
				// pick the most loaded Rack among them.
				int hostActivations = RackData.calculateMinHostActivations(entry.getApplication(), rack.getCurrentStatus().getStatusVector(), rack.getRackDescription().getHostDescription());
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
			
			simulation.getLogger().debug(String.format("[Cluster #%d] AppRelocationPolicyLevel2 - App #%d - Found migration target: Rack #%d.",
					manager.getCapability(ClusterManager.class).getCluster().getId(),
					entry.getApplication().getId(),
					targetRack.getId()));
			
			// Found target. Send migration request.
			simulation.sendEvent(new AppMigRequestEvent(targetRack.getRackManager(), entry.getApplication(), entry.getOrigin(), 0));
			
			// Invalidate target Rack's status, as we know it to be incorrect until the next status update arrives.
			targetRack.invalidateStatus(simulation.getSimulationTime());
			
			// Mark Rack as active (in case it was previously inactive).
			targetRack.activateRack();
		}
		// Could not find suitable target Rack in the Cluster.
		else {
			int clusterId = manager.getCapability(ClusterManager.class).getCluster().getId();
			
			// If event's sender belongs in this Cluster, request assistance from DC Manager 
			// to find a target Host for the VM migration in another Cluster.
			if (null != rackPool.getRack(entry.getSender())) {
				
				simulation.getLogger().debug(String.format("[Cluster #%d] AppRelocationPolicyLevel2 - App #%d - Failed to find migration target. Contact DC Manager.",
						manager.getCapability(ClusterManager.class).getCluster().getId(),
						entry.getApplication().getId()));
				
				simulation.sendEvent(new AppMigRequestEvent(target, entry.getApplication(), entry.getOrigin(), clusterId));
			}
			// Event's sender does not belong in this Cluster.
			else {
				// Migration request was sent by DC Manager. Reject migration request.
				
				simulation.getLogger().debug(String.format("[Cluster #%d] AppRelocationPolicyLevel2 - App #%d - Failed to find migration target. Reject migration request.",
						manager.getCapability(ClusterManager.class).getCluster().getId(),
						entry.getApplication().getId()));
				
				simulation.sendEvent(new AppMigRejectEvent(target, entry.getApplication(), entry.getOrigin(), clusterId));
			}
			
			// In any case, delete entry from migration requests record.
			// If requested assistance from DC Manager, I'm not seeing this request again.
			// If rejected the request, I'm not seeing this request again.
			manager.getCapability(MigRequestRecord.class).removeEntry(entry);
		}
	}
	
	/**
	 * Returns the subset of active Racks in the given list. The sublist preserves the order of 
	 * the elements in the original list.
	 */
	protected ArrayList<RackData> getActiveRacksSublist(ArrayList<RackData> racks) {
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
	protected RackData getInactiveRack(ArrayList<RackData> racks) {
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
