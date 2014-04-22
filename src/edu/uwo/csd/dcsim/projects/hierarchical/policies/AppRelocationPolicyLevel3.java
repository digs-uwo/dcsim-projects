package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import edu.uwo.csd.dcsim.management.HostDescription;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.projects.hierarchical.AppStatus;
import edu.uwo.csd.dcsim.projects.hierarchical.ClusterData;
import edu.uwo.csd.dcsim.projects.hierarchical.ClusterDataComparator;
import edu.uwo.csd.dcsim.projects.hierarchical.ClusterStatus;
import edu.uwo.csd.dcsim.projects.hierarchical.MigRequestEntry;
import edu.uwo.csd.dcsim.projects.hierarchical.RackStatusVector;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.ClusterPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.MigRequestRecord;
import edu.uwo.csd.dcsim.projects.hierarchical.events.AppMigRequestEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.AppMigRejectEvent;

/**
 * 
 * 
 * @author Gaston Keller
 *
 */
public class AppRelocationPolicyLevel3 extends Policy {

	/**
	 * Creates an instance of AppRelocationPolicyLevel3.
	 */
	public AppRelocationPolicyLevel3() {
		addRequiredCapability(ClusterPoolManager.class);
		addRequiredCapability(MigRequestRecord.class);
	}
	
	/**
	 * This event can only come from a Cluster in the Data Centre.
	 */
	public void execute(AppMigRequestEvent event) {
		
		simulation.getLogger().debug("[DC Manager] AppRelocationPolicyLevel3 - New MigRequest - App #" + event.getApplication().getId());
		
		MigRequestEntry entry = new MigRequestEntry(event.getApplication(), event.getOrigin(), event.getSender());
		
		// Store info about migration request just received.
		manager.getCapability(MigRequestRecord.class).addEntry(entry);
		
		this.searchForAppMigrationTarget(entry);
	}
	
	/**
	 * This event can only come from a Cluster in response to a migration request sent by the DC Manager.
	 */
	public void execute(AppMigRejectEvent event) {
		
		simulation.getLogger().debug("[DC Manager] AppPlacementPolicyLevel3 - New Migration reject - App #" + event.getApplication().getId());
		
		// Mark sender's status as invalid (to avoid choosing sender again in the next step).
		Collection<ClusterData> clusters = manager.getCapability(ClusterPoolManager.class).getClusters();
		for (ClusterData cluster : clusters) {
			if (cluster.getId() == event.getSender()) {
				cluster.invalidateStatus(simulation.getSimulationTime());
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
		ClusterData targetCluster = null;
		
		simulation.getLogger().debug("[DC Manager] AppRelocationPolicyLevel3 - Searching for target Cluster...");
		
		ArrayList<ClusterData> clusters = new ArrayList<ClusterData>(manager.getCapability(ClusterPoolManager.class).getClusters());
		
		// Create sublist of Clusters that have the required HW capabilities to host the application.
		ArrayList<ClusterData> candidates = this.getCapableClustersSublist(entry.getApplication(), clusters);
		
		// Sort candidate Clusters in decreasing order by power efficiency.
		// TODO Since Power Efficiency is a static metric, the ClusterPoolManager could maintain 
		// the list of Clusters sorted (at insertion time) and in that way the list would not 
		// have to be sorted here every time.
		Collections.sort(candidates, ClusterDataComparator.getComparator(ClusterDataComparator.POWER_EFFICIENCY));
		Collections.reverse(candidates);
		
		// Create (ordered) sublist of active Clusters (includes Clusters with currently Invalid Status).
		ArrayList<ClusterData> active = this.getActiveClustersSublist(candidates);
		
		// If there's only one active Cluster, the migration request has to have come from there, 
		// so we need to start a new Cluster.
		if (active.size() == 1) {
			targetCluster = this.getInactiveCluster(clusters);
		}
		else {
			// Search for a target Cluster among the subset of active Clusters.
			
			// For each PowerEff set (that is, the set composed by Clusters of the same PowerEff),
			// perform the following 2 tasks:
			// 
			// 		1. Find the Cluster with the least loaded Rack (i.e., the Rack with the smallest
			//		   number of active Hosts); and
			//		2. Find the Cluster with the largest number of active Racks, but that still has
			//		   some more Racks to activate.
			// 
			// When we change PowerEff sets, check whether any Cluster that could take the VM was
			// identified during the iterations over the previous PowerEff set. If so, terminate
			// the loop and forward the placement request. Otherwise, continue the search over the
			// new PowerEff set.
			
			int minActiveHosts = Integer.MAX_VALUE;
			ClusterData leastLoadedRack = null;
			int maxActiveRacks = 0;
			ClusterData mostLoadedCluster = null;
			
			double currentPowerEff = 0;
			
			for (ClusterData cluster : active) {
				// Filter out Clusters with a currently invalid status.
				// Filter out also the Cluster that sent the request.
				if (!cluster.isStatusValid() || cluster.getId() == entry.getSender())
					continue;
				
				if (currentPowerEff != cluster.getClusterDescription().getPowerEfficiency()) {
					// Check if the Cluster with the least loaded Rack can take the application.
					if (null != leastLoadedRack
							&& leastLoadedRack.getCurrentStatus().getStatusVector() != null
							&& ClusterData.canHost(entry.getApplication(), leastLoadedRack.getCurrentStatus().getStatusVector(), leastLoadedRack.getClusterDescription())) {
						
						targetCluster = leastLoadedRack;
						break;
					}
					// Otherwise, make the most loaded Cluster the target.
					else if (null != mostLoadedCluster) {
						targetCluster = mostLoadedCluster;
						break;
					}
					
					currentPowerEff = cluster.getClusterDescription().getPowerEfficiency();
				}
				
				ClusterStatus status = cluster.getCurrentStatus();
				
				// Find the Cluster with the least loaded Rack (i.e., the Rack with the smallest
				// number of active Hosts).
				RackStatusVector statusVector = status.getStatusVector();
				if (null != statusVector && statusVector.vector[statusVector.iActive] < minActiveHosts) {
					minActiveHosts = statusVector.vector[statusVector.iActive];
					leastLoadedRack = cluster;
				}
				
				// Find the Cluster with the largest number of active Racks, but that still has
				// some more Racks to activate.
				// TODO This check assumes that Racks are active or inactive, never failed.
				if (status.getActiveRacks() > maxActiveRacks && status.getActiveRacks() < cluster.getClusterDescription().getRackCount()) {
					maxActiveRacks = status.getActiveRacks();
					mostLoadedCluster = cluster;
				}
			}
			
			// If we exited the previous loop after parsing all the Clusters in the list, then we haven't checked
			// whether any target Cluster had been identified during the iteration over the last PowerEff set.
			if (null == targetCluster) {
				// Check if the Cluster with the least loaded Rack can take the application.
				if (null != leastLoadedRack
						&& leastLoadedRack.getCurrentStatus().getStatusVector() != null
						&& ClusterData.canHost(entry.getApplication(), leastLoadedRack.getCurrentStatus().getStatusVector(), leastLoadedRack.getClusterDescription())) {
					
					targetCluster = leastLoadedRack;
				}
				// Otherwise, make the most loaded Cluster the target.
				else if (null != mostLoadedCluster) {
					targetCluster = mostLoadedCluster;
				}
			}
			
			// If we have not found a target Cluster among the subset of active Clusters (and there are still
			// some inactive Clusters available), then activate a new candidate Cluster.
			if (null == targetCluster && active.size() < candidates.size()) {
				targetCluster = this.getInactiveCluster(candidates);
			}
		}
		
		if (null != targetCluster) {
			
			simulation.getLogger().debug("[DC Manager] Found relocation target: Cluster #" + targetCluster.getId());
			
			// Found target. Send migration request.
			simulation.sendEvent(new AppMigRequestEvent(targetCluster.getClusterManager(), entry.getApplication(), entry.getOrigin(), 0));
			
			// Invalidate target Cluster's status, as we know it to be incorrect until the next status update arrives.
			targetCluster.invalidateStatus(simulation.getSimulationTime());
			
			// Mark Cluster as active (in case it was previously inactive).
			targetCluster.activateCluster();
		}
		// Could not find suitable target Cluster in the Data Centre.
		else {
			
			simulation.getLogger().debug("[DC Manager] Failed to find relocation target.");
			
			// Contact RackManager origin to reject migration request.
			simulation.sendEvent(new AppMigRejectEvent(entry.getOrigin(), entry.getApplication(), entry.getOrigin(), 0));
			
			// Delete entry from migration requests record.
			manager.getCapability(MigRequestRecord.class).removeEntry(entry);
		}
	}
	
	/**
	 * Returns the subset of active Clusters in the given list. The sublist preserves the order of 
	 * the elements in the original list.
	 */
	protected ArrayList<ClusterData> getActiveClustersSublist(ArrayList<ClusterData> clusters) {
		ArrayList<ClusterData> active = new ArrayList<ClusterData>();
		for (ClusterData cluster : clusters) {
			if (cluster.isClusterActive())
				active.add(cluster);
		}
		
		return active;
	}
	
	/**
	 * Returns the subset of Clusters from the given list that have the required HW capabilities
	 * (i.e., core count and core capacity) to host the application. The sublist preserves the order
	 * of the elements in the original list.
	 */
	protected ArrayList<ClusterData> getCapableClustersSublist(AppStatus application, ArrayList<ClusterData> clusters) {
		ArrayList<ClusterData> capable = new ArrayList<ClusterData>();
		
		// Get maximum #cores and maximum core capacity from the VMs in the request.
		int maxReqCores = 0;
		int maxReqCoreCapacity = 0;
		for (VmStatus vm : application.getAllVms()) {
			if (vm.getCores() > maxReqCores)
				maxReqCores = vm.getCores();
			
			if (vm.getCoreCapacity() > maxReqCoreCapacity)
				maxReqCoreCapacity = vm.getCoreCapacity();
		}
		
		// For each Cluster, determine whether its Hosts have the required HW capabilities (i.e., core count and core capacity).
		for (ClusterData cluster : clusters) {
			HostDescription hostDescription = cluster.getClusterDescription().getRackDescription().getHostDescription();
			if (hostDescription.getCpuCount() * hostDescription.getCoreCount() >= maxReqCores && hostDescription.getCoreCapacity() >= maxReqCoreCapacity)
				capable.add(cluster);
		}
		
		return capable;
	}
	
	/**
	 * Returns the first inactive Cluster found in the given list.
	 * 
	 * This method may return NULL if the list contains no inactive Clusters.
	 */
	protected ClusterData getInactiveCluster(ArrayList<ClusterData> clusters) {
		for (ClusterData cluster : clusters) {
			if (!cluster.isClusterActive())
				return cluster;
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
