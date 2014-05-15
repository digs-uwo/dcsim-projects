package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.management.HostDescription;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.events.ApplicationPlacementEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.ClusterData;
import edu.uwo.csd.dcsim.projects.hierarchical.ClusterDataComparator;
import edu.uwo.csd.dcsim.projects.hierarchical.ClusterStatus;
import edu.uwo.csd.dcsim.projects.hierarchical.ConstrainedAppAllocationRequest;
import edu.uwo.csd.dcsim.projects.hierarchical.RackStatusVector;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.ClusterPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.events.PlacementRejectEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.PlacementRequestEvent;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;

/**
 * This policy places applications, one at a time, in a data centre. 
 * At this level, the policy selects a target Cluster to which to forward the 
 * Application Placement request.
 * 
 * @author Gaston Keller
 *
 */
public class AppPlacementPolicyLevel3 extends Policy {

	/**
	 * Creates an instance of AppPlacementPolicyLevel3.
	 */
	public AppPlacementPolicyLevel3() {
		addRequiredCapability(ClusterPoolManager.class);
	}
	
	/**
	 * This method processes one Application Placement request at a time.
	 */
	public void execute(ApplicationPlacementEvent event) {
		
		for (Application application : event.getApplications()) {
			
			if (!this.processRequest(new ConstrainedAppAllocationRequest((InteractiveApplication) application))) {
				event.setFailed(true);
				
				simulation.getLogger().debug("[DC Manager] AppPlacementPolicyLevel3 - PLACEMENT FAILED - App #" + application.getId());
				System.out.println("AppPlacementPolicyLevel3 - PLACEMENT FAILED - App #" + application.getId());
				
				// Record failure to complete placement request.
				if (simulation.isRecordingMetrics()) {
					simulation.getSimulationMetrics().getApplicationMetrics().incrementApplicationPlacementsFailed();
				}
			}
		}
	}
	
	/**
	 * This event can only come from a Cluster in response to a placement request sent by the DC Manager.
	 */
	public void execute(PlacementRejectEvent event) {
		
		simulation.getLogger().debug("[DC Manager] AppPlacementPolicyLevel3 - New Placement reject - App #" + event.getRequest().getId());
		
		// Mark sender's status as invalid (to avoid choosing sender again in the next step).
		Collection<ClusterData> clusters = manager.getCapability(ClusterPoolManager.class).getClusters();
		for (ClusterData cluster : clusters) {
			if (cluster.getId() == event.getSender()) {
				cluster.invalidateStatus(simulation.getSimulationTime());
				break;
			}
		}
		
		// Search again for a placement target.
		if (!this.processRequest(event.getRequest())) {
			
			simulation.getLogger().debug("[DC Manager] AppPlacementPolicyLevel3 - PLACEMENT FAILED AFTER REJECT - App #" + event.getRequest().getId());
			System.out.println("AppPlacementPolicyLevel3 - PLACEMENT FAILED AFTER REJECT - App #" + event.getRequest().getId());
			
			// Record failure to complete placement request.
			if (simulation.isRecordingMetrics()) {
				simulation.getSimulationMetrics().getApplicationMetrics().incrementApplicationPlacementsFailed();
			}
		}
	}
	
	protected boolean processRequest(ConstrainedAppAllocationRequest request) {
		ClusterData targetCluster = null;
		
		simulation.getLogger().debug("[DC Manager] AppPlacementPolicyLevel3.processRequest() - App #" + request.getId());
		
		ArrayList<ClusterData> clusters = new ArrayList<ClusterData>(manager.getCapability(ClusterPoolManager.class).getClusters());
		
		// Create sublist of Clusters that have the required HW capabilities to host the application.
		ArrayList<ClusterData> candidates = this.getCapableClustersSublist(request, clusters);
		
		// Sort candidate Clusters in decreasing order by power efficiency.
		// TODO Since Power Efficiency is a static metric, the ClusterPoolManager could maintain 
		// the list of Clusters sorted (at insertion time) and in that way the list would not 
		// have to be sorted here every time.
		Collections.sort(candidates, ClusterDataComparator.getComparator(ClusterDataComparator.POWER_EFFICIENCY));
		Collections.reverse(candidates);
		
		// Create (ordered) sublist of active Clusters (includes Clusters with currently Invalid Status).
		ArrayList<ClusterData> active = this.getActiveClustersSublist(candidates);
		
		// If there are no active Clusters, activate one.
		if (active.size() == 0) {
			targetCluster = this.getInactiveCluster(candidates);
		}
		// If there is only one active Cluster (and its status is valid), check if the Cluster
		// has enough spare resources to host the application; otherwise, activate a new Cluster.
		else if (active.size() == 1) {
			ClusterData cluster = active.get(0);
			if (cluster.isStatusValid()) {
				if (cluster.getCurrentStatus().getActiveRacks() < cluster.getClusterDescription().getRackCount()
						|| (cluster.getCurrentStatus().getStatusVector() != null
							&& ClusterData.canHost(request, cluster.getCurrentStatus().getStatusVector(), cluster.getClusterDescription()))) {
					
					targetCluster = cluster;
				}
			}
			else {
				targetCluster = this.getInactiveCluster(candidates);
			}
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
				if (!cluster.isStatusValid())
					continue;
				
				if (currentPowerEff != cluster.getClusterDescription().getPowerEfficiency()) {
					// Check if the Cluster with the least loaded Rack can take the application.
					if (null != leastLoadedRack
							&& leastLoadedRack.getCurrentStatus().getStatusVector() != null
							&& ClusterData.canHost(request, leastLoadedRack.getCurrentStatus().getStatusVector(), leastLoadedRack.getClusterDescription())) {
						
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
						&& ClusterData.canHost(request, leastLoadedRack.getCurrentStatus().getStatusVector(), leastLoadedRack.getClusterDescription())) {
					
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
			
			simulation.getLogger().debug("[DC Manager] AppPlacementPolicyLevel3.processRequest() - App #" + request.getId() + " - Found placement target: Cluster #" + targetCluster.getId());
			
			// Found target. Send placement request.
			simulation.sendEvent(new PlacementRequestEvent(targetCluster.getClusterManager(), request));
			
			// Invalidate target Cluster's status, as we know it to be incorrect until the next status update arrives.
			targetCluster.invalidateStatus(simulation.getSimulationTime());
			
			// Mark Cluster as active (in case it was previously inactive).
			targetCluster.activateCluster();
			
			return true;
		}
		
		// Could not find suitable target Cluster in the Data Centre.
		simulation.getLogger().debug("[DC Manager] AppPlacementPolicyLevel3.processRequest() - App #" + request.getId() + " - Failed to find placement target.");
		
		return false;
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
	protected ArrayList<ClusterData> getCapableClustersSublist(ConstrainedAppAllocationRequest request, ArrayList<ClusterData> clusters) {
		ArrayList<ClusterData> capable = new ArrayList<ClusterData>();
		
		// Get maximum #cores and maximum core capacity from the VMs in the request.
		int maxReqCores = 0;
		int maxReqCoreCapacity = 0;
		for (VmAllocationRequest req : request.getAllVmAllocationRequests()) {
			if (req.getVMDescription().getCores() > maxReqCores)
				maxReqCores = req.getVMDescription().getCores();
			
			if (req.getVMDescription().getCoreCapacity() > maxReqCoreCapacity)
				maxReqCoreCapacity = req.getVMDescription().getCoreCapacity();
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
