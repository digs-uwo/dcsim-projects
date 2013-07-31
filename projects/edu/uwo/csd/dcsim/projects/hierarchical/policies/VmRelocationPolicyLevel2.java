package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.Collection;
import java.util.Collections;

import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.projects.hierarchical.*;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.ClusterPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.events.MigRequestEvent;

/**
 * 
 * 
 * @author Gaston Keller
 *
 */
public abstract class VmRelocationPolicyLevel2 extends Policy {

	protected AutonomicManager target;
	
	/**
	 * Creates an instance of VmRelocationPolicyLevel2.
	 */
	public VmRelocationPolicyLevel2(AutonomicManager target) {
		addRequiredCapability(ClusterPoolManager.class);
		
		this.target = target;
	}
	
	/**
	 * 
	 */
	public void execute(MigRequestEvent event) {
		ClusterPoolManager clusterPool = manager.getCapability(ClusterPoolManager.class);
		Collection<ClusterData> clusters = clusterPool.getClusters();
		
		
		// TODO Add static efficiency metric to Cluster and ClusterDescription.
		// This metric would probably be something similar to that from Host, CPU_units per Watt.
		
		
		// Sort Clusters in decreasing order by (Power) Efficiency.
		// TODO Since Power Efficiency is a static metric, the ClusterPoolManager could maintain 
		// the list of Clusters sorted (at insertion time) and it wouldn't have to be sorted here 
		// every time.
		Collections.sort(clusters, ClusterDataComparator.getComparator(ClusterDataComparator.EFFICIENCY));
		Collections.reverse(clusters);
		
		
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
		
		ClusterData targetCluster = null;
		
		double currentPowerEff = 0;
		
		for (ClusterData cluster : clusters) {
			// Filter out Clusters with a currently invalid status.
			if (!cluster.isStatusValid())
				continue;
			
			if (currentPowerEff != cluster.getClusterDescription().getEfficiency()) {
				// Check if the Cluster with the most spare capacity has enough resources to take the VM.
				if (null != maxSpareCapacityCluster && this.canHost(event.getVm(), maxSpareCapacityCluster)) {
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
				
				currentPowerEff = cluster.getClusterDescription().getEfficiency();
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
		
		if (null != targetCluster) {
			// Found target. Send migration request.
			simulation.sendEvent(new MigRequestEvent(targetCluster.getClusterManager(), event.getVm(), event.getOrigin()));
			
			
			// TODO Should I record here that a MigRequest for VM X from Host Y was sent and is awaiting response ???
			// Should I store the info in a new capability? Probably...
			// Should I record as well the Host to which the request is forwarded? Don't think so...
			
			
		}
		// else		// Could not find suitable target Cluster in the Data Centre.
		
		// TODO Should I contact RackManager origin to reject/deny the migration request ???
		// MigRejectEvent may sound better...
		// simulation.sendEvent(new MigDeniedEvent(event.getOrigin()));
		
		// TODO Should I record here that a MigRequest for VM X from Host Y was denied ???
		// Should I store the info in a new capability? Probably...
		
	}
	
	/**
	 * 
	 */
	protected boolean canHost(VmStatus vm, ClusterData cluster) {
		// Check Host capabilities (e.g. core count, core capacity).
		HostDescription hostDescription = cluster.getClusterDescription().getRackDescription().getHostDescription();
		if (hostDescription.getCpuCount() * hostDescription.getCoreCount() < vm.getCores())
			return false;
		if (hostDescription.getCoreCapacity() < vm.getCoreCapacity())
			return false;
		
		// Check available resources.
		Resources availableResources = AverageVmSizes.convertCapacityToResources(cluster.getCurrentStatus().getMaxSpareCapacity());
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
