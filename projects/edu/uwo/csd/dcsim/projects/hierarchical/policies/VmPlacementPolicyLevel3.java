package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.events.VmPlacementEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.*;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.ClusterPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.events.VmPlacementRejectEvent;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;

/**
 * ...
 * 
 * @author Gaston Keller
 *
 */
public class VmPlacementPolicyLevel3 extends Policy {

	public static final String REJECTED_PLACEMENTS = "rejectedPlacements";
	
	private static HierarchicalMetrics hierarchicalMetrics = null;
	
	/**
	 * Creates an instance of VmPlacementPolicyLevel3.
	 */
	public VmPlacementPolicyLevel3() {
		addRequiredCapability(ClusterPoolManager.class);
	}
	
	/**
	 * This method processes only one VM Placement request per call. That is, if a list of 
	 * placement request is sent, only the first of said requests will be processed and the 
	 * rest will be marked as FAILED.
	 */
	public void execute(VmPlacementEvent event) {
		boolean process = true;
		for (VmAllocationRequest request : event.getVMAllocationRequests()) {
			if (process) {
				process = false;
				if (!this.searchForVmPlacementTarget(request))
					event.addFailedRequest(request);
			}
			else {
				event.addFailedRequest(request);
			}
		}
	}
	
	/**
	 * This event can only come from a Cluster in response to a placement request sent by the DC Manager.
	 */
	public void execute(VmPlacementRejectEvent event) {
		
		if (hierarchicalMetrics == null) hierarchicalMetrics = HierarchicalTestEnvironment.getHierarchicalMetrics(simulation);
		
		// Mark sender's status as invalid (to avoid choosing sender again in the next step).
		Collection<ClusterData> clusters = manager.getCapability(ClusterPoolManager.class).getClusters();
		for (ClusterData cluster : clusters) {
			if (cluster.getId() == event.getSender()) {
				cluster.invalidateStatus(simulation.getSimulationTime());
				break;
			}
		}
		
		// Search again for a placement target.
		if (!this.searchForVmPlacementTarget(event.getVmAllocationRequest())) {
			// Record failure to complete placement request.
			if (simulation.isRecordingMetrics()) {
				hierarchicalMetrics.rejectedPlacements++;
			}
		}
	}
	
	/**
	 * 
	 */
	protected boolean searchForVmPlacementTarget(VmAllocationRequest request) {
		ClusterPoolManager clusterPool = manager.getCapability(ClusterPoolManager.class);
		ArrayList<ClusterData> clusters = new ArrayList<ClusterData>(clusterPool.getClusters());
		
		ClusterData targetCluster = null;
		
		/**
		 * TODO: Check that the chosen Cluster has the required HW capabilities (cores, core capacity) 
		 * to host the VM. Two ways of doing it:
		 * 1. Modify getInactiveCluster() to also check that the Cluster can host the VM, and add a 
		 *    check in the FOR loop to also skip Clusters that cannot host the VM;
		 * 2. At the beginning of the method, before even sorting the Clusters by power efficiency, 
		 *    parse the list of Clusters and make a sublist with the Clusters that could host the VM.
		 */
		
		// Sort Clusters in decreasing order by power efficiency.
		// TODO Since Power Efficiency is a static metric, the ClusterPoolManager could maintain 
		// the list of Clusters sorted (at insertion time) and in that way the list would not 
		// have to be sorted here every time.
		Collections.sort(clusters, ClusterDataComparator.getComparator(ClusterDataComparator.POWER_EFFICIENCY));
		Collections.reverse(clusters);
		
		// Create (ordered) sublist of active Clusters (includes Clusters with currently Invalid Status).
		ArrayList<ClusterData> active = this.getActiveClustersSublist(clusters);
		
		// If there are no active Clusters, activate one.
		if (active.size() == 0) {
			targetCluster = this.getInactiveCluster(clusters);
		}
		// If there is only one active Cluster (and its status is valid), check if the Rack can host 
		// the VM; otherwise, activate a new Rack.
		else if (active.size() == 1) {
			ClusterData cluster = active.get(0);
			if (cluster.isStatusValid() && this.canHost(request, cluster)) {
				targetCluster = cluster;
			}
			else {
				targetCluster = this.getInactiveCluster(clusters);
			}
		}
		else {
			// Search for a target Cluster among the subset of active Clusters.
			
			// For each PowerEff set (that is, the set composed by Clusters of the same PowerEff), 
			// perform the following 3 tasks:
			// 
			// 		1. Find the Cluster with the Host with the most spare capacity;
			//		2. Find the Cluster with the most loaded Rack (i.e., the Rack with the smallest 
			//		   number of inactive Hosts) that has at least one inactive Host; and
			//		3. Find the Cluster with the largest number of active Racks, but that has still 
			//		   some more Racks to activate.
			// 
			// When we change PowerEff sets, check whether any Cluster that could take the VM was 
			// identified during the iterations over the previous PowerEff set. If so, terminate 
			// the loop and send the migration request. Otherwise, continue the analysis with the 
			// new PowerEff set.
			
			double maxSpareCapacity = 0;
			ClusterData maxSpareCapacityCluster = null;
			int minInactiveHosts = Integer.MAX_VALUE;
			ClusterData mostLoadedRack = null;
			int mostActiveRacks = 0;
			ClusterData mostLoadedCluster = null;
			
			double currentPowerEff = 0;
			
			for (ClusterData cluster : active) {
				// Filter out Clusters with a currently invalid status.
				if (!cluster.isStatusValid())
					continue;
				
				if (currentPowerEff != cluster.getClusterDescription().getPowerEfficiency()) {
					// Check if the Cluster with the most spare capacity has enough resources to take the VM.
					if (null != maxSpareCapacityCluster && this.canHost(request, maxSpareCapacityCluster)) {
						targetCluster = maxSpareCapacityCluster;
						break;
					}
					// Otherwise, make the Cluster with the most loaded Rack the target.
					else if (null != mostLoadedRack) {
						targetCluster = mostLoadedRack;
						break;
					}
					// Last recourse: make the most loaded Cluster the target.
					else if (null != mostLoadedCluster) {
						targetCluster = mostLoadedCluster;
						break;
					}
					
					currentPowerEff = cluster.getClusterDescription().getPowerEfficiency();
					// These two variables could have been modified during the iterations over the previous 
					// PowerEff set.
					maxSpareCapacity = 0;
					maxSpareCapacityCluster = null;
				}
				
				ClusterStatus status = cluster.getCurrentStatus();
				
				// Find the Cluster with the Host with the most spare capacity.
				if (status.getMaxSpareCapacity() > maxSpareCapacity) {
					maxSpareCapacity = status.getMaxSpareCapacity();
					maxSpareCapacityCluster = cluster;
				}
				
				// Find the Cluster with the most loaded Rack (i.e., the Rack with the 
				// smallest number of inactive Hosts) that has at least one inactive Host.
				if (status.getMinInactiveHosts() < minInactiveHosts && status.getMinInactiveHosts() > 0) {
					minInactiveHosts = status.getMinInactiveHosts();
					mostLoadedRack = cluster;
				}
				
				// Find the Cluster with the largest number of active Racks, but that has 
				// still some more Racks to activate.
				// TODO This check assumes that Racks are active or inactive, never failed.
				if (status.getActiveRacks() > mostActiveRacks && status.getActiveRacks() < cluster.getClusterDescription().getRackCount()) {
					mostActiveRacks = status.getActiveRacks();
					mostLoadedCluster = cluster;
				}
			}
			
			// If we have not found a target Cluster among the subset of active Clusters, activate a new Cluster.
			if (null == targetCluster && active.size() < clusters.size()) {
				targetCluster = this.getInactiveCluster(clusters);
			}
		}
		
		if (null != targetCluster) {
			// Found target. Send placement request.
			ArrayList<VmAllocationRequest> requests = new ArrayList<VmAllocationRequest>();
			requests.add(request);
			simulation.sendEvent(new VmPlacementEvent(targetCluster.getClusterManager(), requests));
			
			// Invalidate target Cluster's status, as we know it to be incorrect until the next status update arrives.
			targetCluster.invalidateStatus(simulation.getSimulationTime());
			
			// Mark Cluster as active (if it was previously inactive).
			targetCluster.activateCluster();
			
			return true;
		}
		
		// Could not find suitable target Cluster in the Data Centre.
		return false;
	}
	
	/**
	 * Verifies whether the given Cluster can meet the resource requirements of the VM.
	 */
	protected boolean canHost(VmAllocationRequest request, ClusterData cluster) {
		// Check Host capabilities (e.g. core count, core capacity).
		HostDescription hostDescription = cluster.getClusterDescription().getRackDescription().getHostDescription();
		if (hostDescription.getCpuCount() * hostDescription.getCoreCount() < request.getVMDescription().getCores())
			return false;
		if (hostDescription.getCoreCapacity() < request.getVMDescription().getCoreCapacity())
			return false;
		
		// Check available resources.
		Resources availableResources = StandardVmSizes.convertCapacityToResources(cluster.getCurrentStatus().getMaxSpareCapacity());
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
