package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uwo.csd.dcsim.application.InteractiveTask;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.host.Host.HostState;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.HostDataComparator;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.VmStatusComparator;
import edu.uwo.csd.dcsim.management.action.ConcurrentManagementActionExecutor;
import edu.uwo.csd.dcsim.management.action.MigrationAction;
import edu.uwo.csd.dcsim.management.action.SequentialManagementActionExecutor;
import edu.uwo.csd.dcsim.management.action.ShutdownHostAction;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.TaskInstanceData;
import edu.uwo.csd.dcsim.projects.hierarchical.VmData;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.AppPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.RackManager;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.MigrationTrackingManager;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.VmPoolManager;

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
public class ConsolidationPolicyLevel1 extends Policy {

	protected AutonomicManager target;
	
	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	
	/**
	 * Creates an instance of AppConsolidationPolicyLevel1.
	 */
	public ConsolidationPolicyLevel1(AutonomicManager target, double lowerThreshold, double upperThreshold, double targetUtilization) {
		addRequiredCapability(AppPoolManager.class);
		addRequiredCapability(HostPoolManager.class);
		addRequiredCapability(VmPoolManager.class);
		addRequiredCapability(MigrationTrackingManager.class);
		
		this.target = target;
		
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}
	
	/**
	 * Performs the VM Consolidation process.
	 */
	public void execute() {
		
		simulation.getLogger().debug(String.format("[Rack #%d] ConsolidationPolicyLevel1 - Running...",
				manager.getCapability(RackManager.class).getRack().getId()));
		
		SequentialManagementActionExecutor actionExecutor = new SequentialManagementActionExecutor();
		
		MigrationTrackingManager ongoingMigs = manager.getCapability(MigrationTrackingManager.class);
		Collection<HostData> hosts = manager.getCapability(HostPoolManager.class).getHosts();
		
		// Reset the sandbox host status to the current host status.
		for (HostData host : hosts) {
			host.resetSandboxStatusToCurrent();
		}
		
		// Categorize hosts.
		ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
		ArrayList<HostData> underUtilized = new ArrayList<HostData>();
		ArrayList<HostData> empty = new ArrayList<HostData>();
		this.classifyHosts(hosts, partiallyUtilized, underUtilized, empty);
		
		// Shut down Empty hosts. (This addresses the issue of VMs terminating 
		// and leaving their host empty and powered on.)
		ConcurrentManagementActionExecutor shutdownActions = new ConcurrentManagementActionExecutor();
		for (HostData host : empty) {
			
			if (host.getCurrentStatus().getState() == HostState.OFF)
				continue;
			
			// Ensure that the Host is not involved in any migrations and is not powering on.
			if (host.getCurrentStatus().getIncomingMigrationCount() == 0 && 
				host.getCurrentStatus().getOutgoingMigrationCount() == 0 && 
				host.getCurrentStatus().getState() != HostState.POWERING_ON) {
				
				simulation.getLogger().debug(String.format("[Rack #%d] ConsolidationPolicyLevel1 - Powering OFF (empty) Host #%d.",
						manager.getCapability(RackManager.class).getRack().getId(),
						host.getId()));
				
				shutdownActions.addAction(new ShutdownHostAction(host.getHost()));
			}
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
		
		// TODO: Consolidation actions should probably be triggered sequentially, so as not to create
		// too much overhead on the source and target hosts. However, the resource reservation for the
		// consolidation actions should be issued immediately, so that no other decision making process
		// decides to use the same resources that pending consolidation actions are expecting to use.
		// Given that currently resource reservation doesn't happen until an action is issued, I'm forced
		// to issue consolidation actions concurrently so as to avoid the problem stated above.
		ConcurrentManagementActionExecutor migrations = new ConcurrentManagementActionExecutor();
		//SequentialManagementActionExecutor migrations = new SequentialManagementActionExecutor();
		
		shutdownActions = new ConcurrentManagementActionExecutor();
		
		HostData source = null;
		while (!sources.isEmpty()) {
			source = sources.remove(0);
			ArrayList<VmStatus> vmList = this.orderSourceVms(this.getSourceVms(source));
			
			// Classify source Host's VMs according to the constraint-type of their hosted Task instance.
			ArrayList<VmStatus> independent = new ArrayList<VmStatus>();
			ArrayList<VmStatus> antiAffinity = new ArrayList<VmStatus>();
			ArrayList<VmStatus> affinity = new ArrayList<VmStatus>();
			this.classifyVms(vmList, independent, antiAffinity, affinity);
			
			Map<Integer, HostData> vmHostMap = new HashMap<Integer, HostData>();
			boolean success = true;
			
			// Process Affinity VMs.
			if (!affinity.isEmpty()) {
				
				simulation.getLogger().debug(String.format("[Rack #%d] Consolidating Affinity VMs...",
						manager.getCapability(RackManager.class).getRack().getId()));
				
				Map<Integer, HostData> mapping = this.consolidateAffinityVms(source, affinity, targets);
				if (null != mapping)
					vmHostMap.putAll(mapping);
				else
					success = false;
			}
			
			// Process Anti-affinity VMs.
			if (success && !antiAffinity.isEmpty()) {
				
				simulation.getLogger().debug(String.format("[Rack #%d] Consolidating Anti-affinity VMs...",
						manager.getCapability(RackManager.class).getRack().getId()));
				
				Map<Integer, HostData> mapping = this.consolidateAntiAffinityVms(source, antiAffinity, targets);
				if (null != mapping)
					vmHostMap.putAll(mapping);
				else
					success = false;
			}
			
			// Process Independent VMs.
			if (success && !independent.isEmpty()) {
				
				simulation.getLogger().debug(String.format("[Rack #%d] Consolidating Independent VMs...",
						manager.getCapability(RackManager.class).getRack().getId()));
				
				Map<Integer, HostData> mapping = this.consolidateIndependentVms(source, independent, targets);
				if (null != mapping)
					vmHostMap.putAll(mapping);
				else
					success = false;
			}
			
			if (success) {
				
				assert vmHostMap.size() == vmList.size();
				
				// Invalidate source Host's status, as we know it to be incorrect until the next status update arrives.
				source.invalidateStatus(simulation.getSimulationTime());
				// Remove source Host from list of potential targets.
				targets.remove(source);
				
				for (Map.Entry<Integer, HostData> entry : vmHostMap.entrySet()) {
					HostData target = entry.getValue();
					
					// Invalidate target Host's status, as we know it to be incorrect until the next status update arrives.
					target.invalidateStatus(simulation.getSimulationTime());
					
					// Remove target Host from list of sources.
					sources.remove(target);
					
					// Mark VM as scheduled for migration.
					ongoingMigs.addMigratingVm(entry.getKey());
					
					migrations.addAction(new MigrationAction(source.getHostManager(), source.getHost(),	target.getHost(), entry.getKey()));
					
					simulation.getLogger().debug(String.format("[Rack #%d] ConsolidationPolicyLevel1 - Migrating VM #%d from Host #%d to Host #%d.",
							manager.getCapability(RackManager.class).getRack().getId(),
							entry.getKey(),
							source.getId(),
							target.getHost().getId()));
					
				}
				
				// Source Host will be empty after these migrations, so shut it down.
				shutdownActions.addAction(new ShutdownHostAction(source.getHost()));
			}
			else {
				
				simulation.getLogger().debug(String.format("[Rack #%d] ConsolidationPolicyLevel1 - Failed to completely migrate load away from Host #%d.",
						manager.getCapability(RackManager.class).getRack().getId(),
						source.getId()));
				
				if (!vmHostMap.isEmpty()) {
					// Undo resource reservation on successfully selected target Hosts.
					for (VmStatus vm : vmList) {
						if (vmHostMap.containsKey(vm.getId()))
							source.getSandboxStatus().unmigrate(vm, vmHostMap.get(vm.getId()).getSandboxStatus());
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
	
	/**
	 * Classifies hosts as Partially-Utilized, Under-Utilized or Empty based 
	 * on the Hosts' average CPU utilization over the last window of time. The 
	 * method ignores (or discards) Stressed Hosts.
	 */
	protected void classifyHosts(Collection<HostData> hosts, ArrayList<HostData> partiallyUtilized, ArrayList<HostData> underUtilized, ArrayList<HostData> empty) {
		
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
	 * Classifies VMs as Independent, Anti-affinity, or Affinity, in accordance with the constraint-type
	 * of their hosted Task instance.
	 */
	protected void classifyVms(ArrayList<VmStatus> vms, ArrayList<VmStatus> independent, ArrayList<VmStatus> antiAffinity, ArrayList<VmStatus> affinity) {
		AppPoolManager appPool = manager.getCapability(AppPoolManager.class);
		VmPoolManager vmPool = manager.getCapability(VmPoolManager.class);
		
		for (VmStatus vm : vms) {
			TaskInstanceData vmTask = vmPool.getVm(vm.getId()).getTask();
			
			switch (appPool.getApplication(vmTask.getAppId()).getTask(vmTask.getTaskId()).getConstraintType()) {
			case INDEPENDENT:
				independent.add(vm);
				break;
			case ANTI_AFFINITY:
				antiAffinity.add(vm);
				break;
			case AFFINITY:
				affinity.add(vm);
				break;
			}
		}
	}
	
	private Map<Integer, HostData> consolidateAffinityVms(HostData source, ArrayList<VmStatus> affinity, ArrayList<HostData> targets) {
		Map<Integer, HostData> mapping = new HashMap<Integer, HostData>();
		
		ArrayList<ArrayList<VmStatus>> affinitySets = this.groupVmsByAffinity(affinity);
		
		// If there's more than one Affinity-set, sort them in increasing order by size.
		if (affinitySets.size() > 1)
			Collections.sort(affinitySets, new Comparator<List<?>>(){
				@Override
				public int compare(List<?> arg0, List<?> arg1) {
					return arg0.size() - arg1.size();
				}
			});
		
		for (ArrayList<VmStatus> affinitySet : affinitySets) {
			boolean success = false;
			
			// Calculate total required resources for all the VMs in the Affinity-set.
			int maxReqCores = 0;
			int maxReqCoreCapacity = 0;
			Resources totalReqResources = new Resources();
			for (VmStatus vm : affinitySet) {
				if (vm.getCores() > maxReqCores)
					maxReqCores = vm.getCores();
				
				if (vm.getCoreCapacity() > maxReqCoreCapacity)
					maxReqCoreCapacity = vm.getCoreCapacity();
				
				totalReqResources = totalReqResources.add(vm.getResourcesInUse());
			}
			
			for (HostData target : targets) {
				
				// Check that source and target are different hosts, 
				// that source has lower utilization than target, 
				// that target Host is capable and has enough capacity left to host the VM, 
				// and that the migration won't push the Host's utilization above the target utilization threshold.
				if (source != target &&
					this.calculateHostAvgCpuUtilization(source) < this.calculateHostAvgCpuUtilization(target) &&
					HostData.canHost(maxReqCores, maxReqCoreCapacity, totalReqResources, target.getSandboxStatus(), target.getHostDescription()) &&
					(target.getSandboxStatus().getResourcesInUse().getCpu() + totalReqResources.getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
					
					for (VmStatus vm : affinitySet) {
						// Modify host and VM states to record the future migration. Note that we 
						// can do this because we are using the designated 'sandbox' host status.
						source.getSandboxStatus().migrate(vm, target.getSandboxStatus());
						
						mapping.put(vm.getId(), target);
					}
					
					success = true;
					break;					// Found migration target. Exit loop.
				}
			}
			
			if (!success)					// Failed to find migration target. Exit loop.
				break;
		}
		
		if (!mapping.isEmpty()) {
			if (mapping.size() == affinity.size())
				return mapping;
			else {
				// Undo resource reservation on successfully selected target Hosts.
				for (VmStatus vm : affinity) {
					if (mapping.containsKey(vm.getId()))
						source.getSandboxStatus().unmigrate(vm, mapping.get(vm.getId()).getSandboxStatus());
				}
			}
		}
		
		return null;
	}
	
	private Map<Integer, HostData> consolidateAntiAffinityVms(HostData source, ArrayList<VmStatus> antiAffinity, ArrayList<HostData> targets) {
		Map<Integer, HostData> mapping = new HashMap<Integer, HostData>();
		
		VmPoolManager vmPool = manager.getCapability(VmPoolManager.class);
		
		for (VmStatus vm : antiAffinity) {
			boolean success = false;
			
			for (HostData target : targets) {
				
				// Check that source and target are different hosts, 
				// that source has lower utilization than target, 
				// that target Host is capable and has enough capacity left to host the VM, 
				// that the migration won't push the Host's utilization above the target utilization threshold,
				// and that the target is not already hosting an instance of the Task hosted in the VM.
				if (source != target && 
					this.calculateHostAvgCpuUtilization(source) < this.calculateHostAvgCpuUtilization(target) &&
					HostData.canHost(vm, target.getSandboxStatus(), target.getHostDescription()) && 
					(target.getSandboxStatus().getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization &&
					!this.isHostingTask(vmPool.getVm(vm.getId()).getTask(), target)) {
					
					// Modify host and VM states to record the future migration. Note that we 
					// can do this because we are using the designated 'sandbox' host status.
					source.getSandboxStatus().migrate(vm, target.getSandboxStatus());
					
					mapping.put(vm.getId(), target);
					
					success = true;
					break;					// Found migration target. Exit loop.
				}
			}
			
			if (!success)					// Failed to find migration target. Exit loop.
				break;
		}
		
		if (!mapping.isEmpty()) {
			if (mapping.size() == antiAffinity.size())
				return mapping;
			else {
				// Undo resource reservation on successfully selected target Hosts.
				for (VmStatus vm : antiAffinity) {
					if (mapping.containsKey(vm.getId()))
						source.getSandboxStatus().unmigrate(vm, mapping.get(vm.getId()).getSandboxStatus());
				}
			}
		}
		
		return null;
	}
	
	private Map<Integer, HostData> consolidateIndependentVms(HostData source, ArrayList<VmStatus> independent, ArrayList<HostData> targets) {
		Map<Integer, HostData> mapping = new HashMap<Integer, HostData>();
		
		for (VmStatus vm : independent) {
			boolean success = false;
			
			for (HostData target : targets) {
				
				// Check that source and target are different hosts, 
				// that source has lower utilization than target, 
				// that target Host is capable and has enough capacity left to host the VM, 
				// and that the migration won't push the Host's utilization above the target utilization threshold.
				if (source != target && 
					this.calculateHostAvgCpuUtilization(source) < this.calculateHostAvgCpuUtilization(target) &&
					HostData.canHost(vm, target.getSandboxStatus(), target.getHostDescription()) &&
					(target.getSandboxStatus().getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
					
					// Modify host and VM states to record the future migration. Note that we 
					// can do this because we are using the designated 'sandbox' host status.
					source.getSandboxStatus().migrate(vm, target.getSandboxStatus());
					
					mapping.put(vm.getId(), target);
					
					success = true;
					break;					// Found migration target. Exit loop.
				}
			}
			
			if (!success)					// Failed to find migration target. Exit loop.
				break;
		}
		
		if (!mapping.isEmpty()) {
			if (mapping.size() == independent.size())
				return mapping;
			else {
				// Undo resource reservation on successfully selected target Hosts.
				for (VmStatus vm : independent) {
					if (mapping.containsKey(vm.getId()))
						source.getSandboxStatus().unmigrate(vm, mapping.get(vm.getId()).getSandboxStatus());
				}
			}
		}
		
		return null;
	}
	
	private VmStatus findHostingVm(InteractiveTask task, ArrayList<VmStatus> vms) {
		VmPoolManager vmPool = manager.getCapability(VmPoolManager.class);
		
		for (VmStatus vm : vms) {
			TaskInstanceData vmTask = vmPool.getVm(vm.getId()).getTask();
			if (vmTask.getTaskId() == task.getId() && vmTask.getAppId() == task.getApplication().getId())
				return vm;
		}
		
		return null;
	}
	
	private ArrayList<ArrayList<VmStatus>> groupVmsByAffinity(ArrayList<VmStatus> vms) {
		ArrayList<ArrayList<VmStatus>> affinitySets = new ArrayList<ArrayList<VmStatus>>();
		
		ArrayList<VmStatus> copy = new ArrayList<VmStatus>(vms);
		while (copy.size() > 0) {
			VmStatus vm = copy.get(0);
			
			// Get Affinity-set for the Task instance hosted in this VM.
			TaskInstanceData vmTask = manager.getCapability(VmPoolManager.class).getVm(vm.getId()).getTask();
			ArrayList<InteractiveTask> affinitySet = manager.getCapability(AppPoolManager.class).getApplication(vmTask.getAppId()).getAffinitySet(vmTask.getTaskId());
			
			// Build the set of VMs hosting the Tasks in the previously found Affinity-set.
			ArrayList<VmStatus> affinitySetVms = new ArrayList<VmStatus>();
			for (InteractiveTask task : affinitySet) {
				VmStatus hostingVm = this.findHostingVm(task, copy);
				if (null != hostingVm)
					affinitySetVms.add(hostingVm);
			}
			
			affinitySets.add(affinitySetVms);
			copy.removeAll(affinitySetVms);
		}
		
		return affinitySets;
	}
	
	/**
	 * Determines whether the given Host is hosting an instance of the given Task.
	 */
	private boolean isHostingTask(TaskInstanceData task, HostData host) {
		VmPoolManager vmPool = manager.getCapability(VmPoolManager.class);
		
		for (VmStatus vm : host.getSandboxStatus().getVms()) {
			VmData vmData = vmPool.getVm(vm.getId());
			
			if (null == vmData)								// VM does not belong in this Rack any longer.
				continue;
			
			if (vmData.getHost() != host)					// VM is actually located in another Host in this Rack.
				continue;
			
			TaskInstanceData vmTask = vmData.getTask();
			if (vmTask.getTaskId() == task.getTaskId() && vmTask.getAppId() == task.getAppId())
				return true;
		}
		
		return false;
	}
	
	/**
	 * Sorts VMs in decreasing order by <overall capacity, CPU load> (i.e. <memory, cpu cores, core capacity, CPU load>),
	 * so as to place the _biggest_ VMs first.
	 * 
	 * (Note: since CPU can be oversubscribed, but memory can't, memory takes priority over CPU when comparing VMs
	 * by _size_ (capacity).)
	 */
	protected ArrayList<VmStatus> orderSourceVms(ArrayList<VmStatus> sourceVms) {
		
		if (sourceVms.isEmpty())
			return sourceVms;
		
		ArrayList<VmStatus> sources = new ArrayList<VmStatus>(sourceVms);
		
		// Sort VMs in decreasing order by <overall capacity, CPU load>.
		// (Note: since CPU can be oversubscribed, but memory can't, memory takes priority over CPU when comparing VMs
		// by _size_ (capacity).)
		Collections.sort(sources, VmStatusComparator.getComparator(
				VmStatusComparator.MEMORY, 
				VmStatusComparator.CPU_CORES, 
				VmStatusComparator.CORE_CAP, 
				VmStatusComparator.CPU_IN_USE));
		Collections.reverse(sources);
		
		return sources;
	}
	
	/**
	 * Sorts Underutilized hosts in increasing order by <power efficiency, CPU utilization>.
	 */
	protected ArrayList<HostData> orderSourceHosts(ArrayList<HostData> underUtilized) {
		ArrayList<HostData> sources = new ArrayList<HostData>(underUtilized);
		
		// Sort Underutilized hosts in increasing order by <power efficiency, CPU utilization>.
		Collections.sort(sources, HostDataComparator.getComparator(HostDataComparator.EFFICIENCY, HostDataComparator.CPU_UTIL));
		
		return sources;
	}
	
	/**
	 * Sorts Partially-Utilized and Underutilized hosts in decreasing order by <power efficiency, CPU utilization>.
	 */
	protected ArrayList<HostData> orderTargetHosts(ArrayList<HostData> partiallyUtilized, ArrayList<HostData> underUtilized) {
		ArrayList<HostData> targets = new ArrayList<HostData>();
		
		// Sort Partially-utilized and Underutilized hosts in decreasing order by <power efficiency, CPU utilization>.
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		Collections.sort(targets, HostDataComparator.getComparator(HostDataComparator.EFFICIENCY, HostDataComparator.CPU_UTIL));
		Collections.reverse(targets);
		
		return targets;
	}
	
	/**
	 * Returns the subset of VMs that are not migrating out or scheduled to do so.
	 */
	private ArrayList<VmStatus> getSourceVms(HostData source) {
		ArrayList<VmStatus> output = new ArrayList<VmStatus>();
		
		VmPoolManager vmPool = manager.getCapability(VmPoolManager.class);
		MigrationTrackingManager ongoingMigs = manager.getCapability(MigrationTrackingManager.class);
		
		// Get Host's VMs.
		ArrayList<VmStatus> vms = source.getCurrentStatus().getVms();
		
		// Purge VM list.
		for (VmStatus status : vms) {
			VmData vmData = vmPool.getVm(status.getId());
			
			if (null == vmData)								// VM does not belong in this Rack any longer.
				continue;
			
			if (vmData.getHost() != source)					// VM is actually located in another Host in this Rack.
				continue;
			
			if (ongoingMigs.isMigrating(vmData.getId()))	// VM is migrating out or scheduled to do so.
				continue;
			
			output.add(status);
		}
		
		return output;
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
