package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Host.HostState;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.action.*;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;

/**
 * This policy implements the VM Consolidation process using a greedy algorithm. 
 * VMs are migrated out of Underutilized hosts and into other Underutilized and 
 * Partially-Utilized hosts.
 * 
 * Measures are taken to prevent Underutilized hosts from being used both as 
 * consolidation source and target.
 * 
 * Hosts are classified as Stressed, Partially-Utilized, Underutilized or 
 * Empty based on the hosts' average CPU utilization over the last window of 
 * time.
 * 
 * There's no limit to the number of VMs that can be migrated out of a host.
 * 
 * @author Gaston Keller
 *
 */
public abstract class VmConsolidationPolicyLevel0 extends Policy {

	protected AutonomicManager target;
	
	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	
	/**
	 * Creates an instance of VmConsolidationPolicyLevel0.
	 */
	public VmConsolidationPolicyLevel0(AutonomicManager target, double lowerThreshold, double upperThreshold, double targetUtilization) {
		addRequiredCapability(HostPoolManager.class);
		
		this.target = target;
		
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}
	
	/**
	 * Sorts the candidate VMs in the order in which they are to be considered 
	 * for VM Consolidation.
	 */
	protected abstract ArrayList<VmStatus> orderSourceVms(ArrayList<VmStatus> sourceVms);
	
	/**
	 * Sorts the source hosts in the order in which they are to be considered 
	 * for VM Consolidation.
	 */
	protected abstract ArrayList<HostData> orderSourceHosts(ArrayList<HostData> underUtilized);
	
	/**
	 * Sorts the target hosts (Partially-Utilized, Underutilized and Empty) in 
	 * the order in which they are to be considered for VM Consolidation.
	 */
	protected abstract ArrayList<HostData> orderTargetHosts(ArrayList<HostData> partiallyUtilized, ArrayList<HostData> underUtilized);
	
	/**
	 * Performs the VM Consolidation process.
	 */
	public void execute() {
		SequentialManagementActionExecutor actionExecutor = new SequentialManagementActionExecutor();
		
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		Collection<HostData> hosts = hostPool.getHosts();
		
		// Reset the sandbox host status to the current host status.
		for (HostData host : hosts) {
			host.resetSandboxStatusToCurrent();
		}
		
		// Categorize hosts.
		ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
		ArrayList<HostData> underUtilized = new ArrayList<HostData>();
		ArrayList<HostData> empty = new ArrayList<HostData>();
		
		this.classifyHosts(partiallyUtilized, underUtilized, empty, hosts);
		
		// Shut down Empty hosts. (This addresses the issue of VMs terminating 
		// and leaving their host empty and powered on.)
		ConcurrentManagementActionExecutor shutdownActions = new ConcurrentManagementActionExecutor();
		for (HostData host : empty) {
			// Ensure that the host is not involved in any migrations and is not powering on.
			if (host.getCurrentStatus().getIncomingMigrationCount() == 0 && 
				host.getCurrentStatus().getOutgoingMigrationCount() == 0 && 
				host.getCurrentStatus().getState() != HostState.POWERING_ON)
				
				shutdownActions.addAction(new ShutdownHostAction(host.getHost()));
		}
		actionExecutor.addAction(shutdownActions);
		
		// Filter out potential source hosts that have incoming migrations.
		ArrayList<HostData> unsortedSources = new ArrayList<HostData>();
		for (HostData host : underUtilized) {
			if (host.getCurrentStatus().getIncomingMigrationCount() == 0) {
				unsortedSources.add(host);
			}
		}
		
		// Create (sorted) source and target lists.
		ArrayList<HostData> sources = this.orderSourceHosts(unsortedSources);
		ArrayList<HostData> targets = this.orderTargetHosts(partiallyUtilized, underUtilized);
		
		HashSet<HostData> usedSources = new HashSet<HostData>();
		HashSet<HostData> usedTargets = new HashSet<HostData>();
		
		ConcurrentManagementActionExecutor migrations = new ConcurrentManagementActionExecutor();
		shutdownActions = new ConcurrentManagementActionExecutor();
		
		for (HostData source : sources) {
			if (!usedTargets.contains(source)) { 	// Check that the source host hasn't been used as a target.
				
				ArrayList<VmStatus> vmList = this.orderSourceVms(source.getCurrentStatus().getVms());
				for (VmStatus vm : vmList) {
					for (HostData target : targets) {
						// Check that source and target are different hosts, 
						// that target host hasn't been used as source, 
						// that target host is capable and has enough capacity left to host the VM, 
						// and also that it will not exceed the target utilization.
						if (source != target && 
								!usedSources.contains(target) && 
								HostData.canHost(vm, target.getSandboxStatus(), target.getHostDescription()) &&	
								(target.getSandboxStatus().getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
							
							// Modify host and vm states to record the future migration. Note that we 
							// can do this because we are using the designated 'sandbox' host status.
							source.getSandboxStatus().migrate(vm, target.getSandboxStatus());
							
							// Invalidate source and target status, as we know them to be incorrect until the next status update arrives.
							source.invalidateStatus(simulation.getSimulationTime());
							target.invalidateStatus(simulation.getSimulationTime());
							
							migrations.addAction(new MigrationAction(source.getHostManager(), source.getHost(),	target.getHost(), vm.getId()));
							
							// If the host will be empty after this migration, instruct it to shut down.
							if (source.getSandboxStatus().getVms().size() == 0) {
								shutdownActions.addAction(new ShutdownHostAction(source.getHost()));
							}
							
							usedTargets.add(target);
							usedSources.add(source);
							
							break;
						}
					}
				}
			}
		}
		
		// Trigger migrations and shutdowns.
		actionExecutor.addAction(migrations);
		actionExecutor.addAction(shutdownActions);
		actionExecutor.execute(simulation, this);
	}
	
	/**
	 * Classifies hosts as Partially-Utilized, Under-Utilized or Empty based 
	 * on the Hosts' average CPU utilization over the last window of time. The 
	 * method ignores (or discards) Stressed Hosts.
	 */
	protected void classifyHosts(ArrayList<HostData> partiallyUtilized, 
			ArrayList<HostData> underUtilized, 
			ArrayList<HostData> empty,
			Collection<HostData> hosts) {
		
		for (HostData host : hosts) {
			
			// Filter out hosts with a currently invalid status.
			if (host.isStatusValid()) {
				
				double avgCpuUtilization = this.calculateHostAvgCpuUtilization(host);
				
				// Classify hosts.
				if (host.getCurrentStatus().getVms().size() == 0) {
					empty.add(host);
				} else if (avgCpuUtilization < lowerThreshold) {
					underUtilized.add(host);
				} else if (avgCpuUtilization < upperThreshold) {
					partiallyUtilized.add(host);
				}
			}
		}
	}
	
	/**
	 * Calculates Host's average CPU utilization over the last window of time.
	 * 
	 * @return		value in range [0,1] (i.e., percentage)
	 */
	protected double calculateHostAvgCpuUtilization(HostData host) {
		double avgCpuInUse = 0;
		int count = 0;
		for (HostStatus status : host.getHistory()) {
			// Only consider times when the host is powered ON.
			if (status.getState() == Host.HostState.ON) {
				avgCpuInUse += status.getResourcesInUse().getCpu();
				++count;
			}
			else
				break;
		}
		if (count != 0) {
			avgCpuInUse = avgCpuInUse / count;
		}
		
		return Utility.roundDouble(avgCpuInUse / host.getHostDescription().getResourceCapacity().getCpu());
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
