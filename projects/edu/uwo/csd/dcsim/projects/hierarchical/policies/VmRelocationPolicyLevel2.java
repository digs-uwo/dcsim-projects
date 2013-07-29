package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.Collection;

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
		
		
		// DO SOMETHING...
		
		
		ClusterData targetCluster = null;
		
		
		// DO SOMETHING...
		
		
		if (null != targetCluster) {
			// Found target. Send migration request.
			simulation.sendEvent(new MigRequestEvent(targetCluster.getClusterManager(), event.getVm(), event.getOrigin()));
			
			
			// TODO Should I record here that a MigRequest for VM X from Host Y was sent and is awaiting response ???
			// Should I store the info in a new capability? Probably...
			// Should I record as well the Host to which the request is forwarded? Don't think so...
			
			
		}
		
		// Could not find suitable target Cluster in the Data Centre.
		
		// TODO Should I contact RackManager origin to reject/deny the migration request ???
		// MigRejectEvent may sound better...
		// simulation.sendEvent(new MigDeniedEvent(event.getOrigin()));
		
		// TODO Should I record here that a MigRequest for VM X from Host Y was denied ???
		// Should I store the info in a new capability? Probably...
		
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
		Resources availableResources = rack.getCurrentStatus().getMaxSpareResources();
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
