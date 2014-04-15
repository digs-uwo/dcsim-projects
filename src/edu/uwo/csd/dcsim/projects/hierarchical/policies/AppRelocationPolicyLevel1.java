package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.application.InteractiveTask;
import edu.uwo.csd.dcsim.application.Task;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.action.InstantiateVmAction;
import edu.uwo.csd.dcsim.management.action.MigrationAction;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.projects.centralized.events.StressCheckEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.AppStatus;
import edu.uwo.csd.dcsim.projects.hierarchical.ConstrainedAppAllocationRequest;
import edu.uwo.csd.dcsim.projects.hierarchical.MigRequestEntry;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.*;
import edu.uwo.csd.dcsim.projects.hierarchical.events.*;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;

/**
 * This policy implements the VM Relocation process in two steps. First, the 
 * policy tries to find a VM migration within the scope of the Hosts pool it 
 * controls. Then, if that fails, the policy contacts a higher-level manager 
 * to request assistance finding a target Host outside of the policy's Hosts 
 * pool to which to migrate a VM.
 * 
 * The first step in the VM Relocation process is labelled *Internal* and 
 * consists of a greedy algorithm that attempts to migrate a VM away from the 
 * Stressed Host and into a Partially-Utilized, Under-Utilized or Empty Host. 
 * Hosts are classified as Stressed, Partially-Utilized, Under-Utilized or 
 * Empty based on the hosts' average CPU utilization over the last window of 
 * time.
 * 
 * The second step in the VM Relocation process is labelled *External* and 
 * consists of selecting a VM to be migrated away from the Stressed Host and 
 * sending the VM's information to the higher-level manager for it to find a 
 * suitable new Host for the VM.
 * 
 * @author Gaston Keller
 *
 */
public class AppRelocationPolicyLevel1 extends Policy {
	
	protected AutonomicManager target;
	
	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	
	/**
	 * Creates an instance of AppRelocationPolicyLevel1.
	 */
	public AppRelocationPolicyLevel1(AutonomicManager target, double lowerThreshold, double upperThreshold, double targetUtilization) {
		addRequiredCapability(HostPoolManager.class);
		addRequiredCapability(MigRequestRecord.class);
		
		this.target = target;
		
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}
	
	/**
	 * This event can only come from another Rack Manager with information about the selected target Host.
	 */
	public void execute(MigAcceptEvent event) {
		
		
		// TODO
		
		
		// Get entry from migration requests record.
		MigRequestEntry entry = manager.getCapability(MigRequestRecord.class).getEntry(event.getVm(), manager);
		HostData source = entry.getHost();
		
		// Invalidate source Host' status, as we know it to be incorrect until the next status update arrives.
		source.invalidateStatus(simulation.getSimulationTime());
		
		// Trigger migration.
		new MigrationAction(source.getHostManager(), source.getHost(), event.getTargetHost(), event.getVm().getId()).execute(simulation, this);
		
		// Delete entry from migration requests record.
		manager.getCapability(MigRequestRecord.class).removeEntry(entry);
	}
	
	/**
	 * This event can only come from the DC Manager, signaling that nobody can accept the migration request.
	 */
	public void execute(AppMigRejectEvent event) {
		// Delete entry from migration requests record.
		MigRequestRecord record = manager.getCapability(MigRequestRecord.class);
		record.removeEntry(record.getEntry(event.getApplication(), event.getOrigin()));
	}
	
	/**
	 * This event comes from the Cluster Manager, trying to migrate an application to this Rack.
	 */
	public void execute(AppMigRequestEvent event) {
		
		// Find target Hosts in this Rack for the VMs that compose the migrating application.
		Map<Integer, HostData> vmHostMap = this.findMigrationTargets(event.getApplication());
		
		// If found, send message to RackManager origin accepting the migration request.
		if (null != vmHostMap) {
			// Invalidate target Hosts' status, as we know them to be incorrect until the next status updates arrive.
			// Build map < vmId , Host > .
			Map<Integer, Host> targets = new HashMap<Integer, Host>();
			for (Map.Entry<Integer, HostData> entry : vmHostMap.entrySet()) {
				entry.getValue().invalidateStatus(simulation.getSimulationTime());
				targets.put(entry.getKey(), entry.getValue().getHost());
			}
			
			simulation.sendEvent(new AppMigAcceptEvent(event.getOrigin(), event.getApplication(), targets));
		}
		else {	// Otherwise, send message to ClusterManager rejecting the migration request.
			int rackId = manager.getCapability(RackManager.class).getRack().getId();
			simulation.sendEvent(new AppMigRejectEvent(target, event.getApplication(), event.getOrigin(), rackId));
		}
	}
	
	/**
	 * Performs a stress check on the Host indicated by the event. If the host is stressed,
	 * it initiates a Relocation process.
	 */
	public void execute(StressCheckEvent event) {
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		
		if (this.isStressed(hostPool.getHost(event.getHostId()))) {
			
			// Perform VM Relocation process within the scope of the Rack.
			// If it fails, request assistance from ClusterManager.
			boolean success = this.performInternalVmRelocation(event.getHostId());
			if (!success)
				this.performExternalVmRelocation(event.getHostId());
		}
	}
	
	/**
	 * Search for a target Host that could take the given VM.
	 */
//	protected HostData findTargetHost(VmStatus vm) {
//		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
//		Collection<HostData> hosts = hostPool.getHosts();
//		
//		// Reset the sandbox host status to the current host status.
//		for (HostData host : hosts) {
//			host.resetSandboxStatusToCurrent();
//		}
//		
//		// Classify Hosts as Partially-Utilized, Under-Utilized or Empty; ignore Stressed Hosts.
//		ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
//		ArrayList<HostData> underUtilized = new ArrayList<HostData>();
//		ArrayList<HostData> empty = new ArrayList<HostData>();
//		this.classifyHosts(hosts, partiallyUtilized, underUtilized, empty);
//		
//		// Create sorted list of target Hosts.
//		ArrayList<HostData> targets = this.orderTargetHosts(partiallyUtilized, underUtilized, empty);
//		
//		for (HostData target : targets) {
//			// Check that target host has at most 1 incoming migration pending, 
//			// that target host is capable and has enough capacity left to host the VM, 
//			// and also that it will not exceed the target utilization.
//			if (target.getSandboxStatus().getIncomingMigrationCount() < 2 && 
//				HostData.canHost(vm, target.getSandboxStatus(), target.getHostDescription()) && 
//				(target.getSandboxStatus().getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
//				
//				return target;
//			}
//		}
//		
//		return null;
//	}
	
	/**
	 * Performs the Relocation process within the scope of the Rack. The process searches for
	 * a feasible VM migration with target Host inside the Rack.
	 */
	protected boolean performInternalVmRelocation(int hostId) {
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		Collection<HostData> hosts = hostPool.getHosts();
		
		// Reset the sandbox host status to the current host status.
		for (HostData host : hosts) {
			host.resetSandboxStatusToCurrent();
		}
		
		// Stressed Host becomes the source for the VM migration.
		HostData source = hostPool.getHost(hostId);
		
		// Classify Hosts as Partially-Utilized, Under-Utilized or Empty; ignore Stressed Hosts
		// and Hosts with currently invalid status.
		ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
		ArrayList<HostData> underUtilized = new ArrayList<HostData>();
		ArrayList<HostData> empty = new ArrayList<HostData>();
		this.classifyHosts(hosts, partiallyUtilized, underUtilized, empty);
		
		// Create sorted list of target Hosts.
		ArrayList<HostData> targets = this.orderTargetHosts(partiallyUtilized, underUtilized, empty);
		
		// Obtain source Host's VMs, and sort them, removing small VMs from consideration for migration.
		ArrayList<VmStatus> vmList = this.orderSourceVms(source.getCurrentStatus().getVms(), source);
		
		// Classify source Host's VMs according to the constraint-type of their hosted Task instance.
		ArrayList<VmStatus> independent = new ArrayList<VmStatus>();
		ArrayList<VmStatus> antiAffinity = new ArrayList<VmStatus>();
		ArrayList<VmStatus> affinity = new ArrayList<VmStatus>();
		this.classifyVms(vmList, independent, antiAffinity, affinity);
		
		// Process Independent VMs.
		MigrationAction mig = null;
		for (VmStatus vm : independent) {
			
			for (HostData target : targets) {
				// Check that the target host is not currently involved in migrations,
				// that the target host has enough capacity left to host the VM, 
				// and that the migration won't push the Host's utilization above the target utilization threshold.
				if (target.getSandboxStatus().getIncomingMigrationCount() == 0 && target.getSandboxStatus().getOutgoingMigrationCount() == 0 &&
					HostData.canHost(vm, target.getSandboxStatus(), target.getHostDescription()) && 
					(target.getSandboxStatus().getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
					
					// Modify host and VM states to record the future migration. Note that we 
					// can do this because we are using the designated 'sandbox' host status.
					source.getSandboxStatus().migrate(vm, target.getSandboxStatus());
					
					// Invalidate source and target status, as we know them to be incorrect until the next status update arrives.
					source.invalidateStatus(simulation.getSimulationTime());
					target.invalidateStatus(simulation.getSimulationTime());
					
					mig = new MigrationAction(source.getHostManager(), source.getHost(), target.getHost(), vm.getId());
					
					break;		// Found VM migration. Exit loop.
				}
			}
			
			if (null != mig)	// Found VM migration. Exit loop.
				break;
		}
		
		if (null != mig) {		// Trigger migration and exit.
			mig.execute(simulation, this);
			return true;
		}
		
		// Process Anti-affinity VMs.
		for (VmStatus vm : antiAffinity) {
			
			for (HostData target : targets) {
				// Check that the target host is not currently involved in migrations,
				// that the target host has enough capacity left to host the VM, 
				// and that the migration won't push the Host's utilization above the target utilization threshold.
				if (target.getSandboxStatus().getIncomingMigrationCount() == 0 && target.getSandboxStatus().getOutgoingMigrationCount() == 0 &&
					HostData.canHost(vm, target.getSandboxStatus(), target.getHostDescription()) && 
					(target.getSandboxStatus().getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization &&
					!this.isHostingTask(vm.getVm().getTaskInstance().getTask(), target)) {
					
					// Modify host and VM states to record the future migration. Note that we 
					// can do this because we are using the designated 'sandbox' host status.
					source.getSandboxStatus().migrate(vm, target.getSandboxStatus());
					
					// Invalidate source and target status, as we know them to be incorrect until the next status update arrives.
					source.invalidateStatus(simulation.getSimulationTime());
					target.invalidateStatus(simulation.getSimulationTime());
					
					mig = new MigrationAction(source.getHostManager(), source.getHost(), target.getHost(), vm.getId());
					
					break;		// Found VM migration. Exit loop.
				}
			}
			
			if (null != mig)	// Found VM migration. Exit loop.
				break;
		}
		
		if (null != mig) {		// Trigger migration and exit.
			mig.execute(simulation, this);
			return true;
		}
		
		// Process Affinity VMs.
		ArrayList<MigrationAction> migActions = new ArrayList<MigrationAction>();
		
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
			
			// Calculata total required resources for all the VMs in the Affinity-set.
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
				// Check that the target host is not currently involved in migrations,
				// that the target host has enough capacity left to host the VM, 
				// and that the migration won't push the Host's utilization above the target utilization threshold.
				if (target.getSandboxStatus().getIncomingMigrationCount() == 0 && target.getSandboxStatus().getOutgoingMigrationCount() == 0 &&
					HostData.canHost(maxReqCores, maxReqCoreCapacity, totalReqResources, target.getSandboxStatus(), target.getHostDescription()) && 
					(target.getSandboxStatus().getResourcesInUse().getCpu() + totalReqResources.getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
					
					// Modify host and VM states to record the future migration. Note that we 
					// can do this because we are using the designated 'sandbox' host status.
					// Create a migration action per VM in the Affinity-set.
					for (VmStatus v : affinitySet) {
						source.getSandboxStatus().migrate(v, target.getSandboxStatus());
						migActions.add(new MigrationAction(source.getHostManager(), source.getHost(), target.getHost(), v.getId()));
					}
					
					// Invalidate source and target status, as we know them to be incorrect until the next status update arrives.
					source.invalidateStatus(simulation.getSimulationTime());
					target.invalidateStatus(simulation.getSimulationTime());
					
					break;					// Found VM migration. Exit loop.
				}
			}
			
			if (migActions.size() > 0)		// Found VM migration. Exit loop.
				break;
		}
		
		if (migActions.size() > 0) {		// Trigger migrations and exit.
			for (MigrationAction action : migActions)
				action.execute(simulation, this);
			return true;
		}
		
		return false;
	}
	
	/**
	 * Starts an external VM Relocation process.
	 * 
	 * An application hosted in a VM in the stressed Host is selected to be migrated away from the Rack.
	 * The ClusterManager is contacted for it to find a target Rack for the migration.
	 */
	protected void performExternalVmRelocation(int hostId) {
		HostData source = manager.getCapability(HostPoolManager.class).getHost(hostId);
		int rackId = manager.getCapability(RackManager.class).getRack().getId();
		
		// Find VM to migrate away.
		VmStatus vm = this.findCandidateVm(source);
		
		// TODO: Accessing remote object (VM). Redesign mgmt. system to avoid this trick.
		
		AppStatus application = new AppStatus((InteractiveApplication) vm.getVm().getTaskInstance().getTask().getApplication());
		
		// Request assistance from ClusterManager to find a target Rack to which to migrate the selected application.
		simulation.sendEvent(new AppMigRequestEvent(target, application, manager, rackId));
		
		// Keep track of the migration request just sent.
		manager.getCapability(MigRequestRecord.class).addEntry(new MigRequestEntry(application, manager, source));
	}
	
	protected Map<Integer, HostData> findMigrationTargets(AppStatus application) {
		Map<Integer, HostData> vmHostMap = new HashMap<Integer, HostData>();
		
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
		
		// Create target hosts list.
		ArrayList<HostData> targets = this.orderTargetHosts(partiallyUtilized, underUtilized, empty);
		
		for (ArrayList<VmStatus> affinitySet : application.getAffinityVms()) {
			Map<Integer, HostData> mapping = this.placeVmsTogether(affinitySet, targets);
			if (null != mapping)
				vmHostMap.putAll(mapping);
			else
				return null;
		}
		
		for (ArrayList<VmStatus> antiAffinitySet : application.getAntiAffinityVms()) {
			Map<Integer, HostData> mapping = this.placeVmsApart(antiAffinitySet, targets);
			if (null != mapping)
				vmHostMap.putAll(mapping);
			else
				return null;
		}
		
		for (VmStatus req : application.getIndependentVms()) {
			HostData targetHost = this.placeVmWherever(req, targets);
			if (null != targetHost)
				vmHostMap.put(req.getId(), targetHost);
			else
				return null;
		}
		
		// If we don't have a Placement action for each allocation request, then there's an implementation error somewhere.
		assert application.getAllVms().size() == vmHostMap.size();
		
		return vmHostMap;
	}
	
	/**
	 * Selects a VM to be migrated away from the Rack containing the given Host.
	 * 
	 * Returns null ONLY if the given Host is not hosting any VMs.
	 */
	protected VmStatus findCandidateVm(HostData source) {
		
		// Obtain source Host's VMs and sort them in increasing order by application size (i.e., the number of VMs
		// that compose the application (partially or completely) hosted in the VM).
		// Obtain source Host's VMs, and sort them, removing small VMs from consideration for migration.
		ArrayList<VmStatus> vmList = source.getCurrentStatus().getVms();
		
		// Determine the amount of CPU load to get rid of to bring the Host back to its target utilization.
		double cpuExcess = source.getSandboxStatus().getResourcesInUse().getCpu() - source.getHostDescription().getResourceCapacity().getCpu() * this.targetUtilization;
		
		VmStatus bigVm = null;
		int bigVmMinAppSize = Integer.MAX_VALUE;
		int bigVmMinLoad = Integer.MAX_VALUE;
		VmStatus smallVm = null;
		int smallVmMinAppSize = Integer.MAX_VALUE;
		int smallVmMaxLoad = Integer.MIN_VALUE;
		for (VmStatus vm : vmList) {
			int vmLoad = vm.getResourcesInUse().getCpu();
			
			// TODO: Accessing remote object (VM). Redesign mgmt. system to avoid this trick.
			
			int vmAppSize = vm.getVm().getTaskInstance().getTask().getApplication().getSize();
			if (vmLoad >= cpuExcess) {
				if (vmAppSize < bigVmMinAppSize) {
					bigVmMinAppSize = vmAppSize;
					bigVmMinLoad = vmLoad;
					bigVm = vm;
				}
				else if (vmAppSize == bigVmMinAppSize) {
					if (vmLoad < bigVmMinLoad) {
						bigVmMinLoad = vmLoad;
						bigVm = vm;
					}
				}
			}
			else {
				if (vmAppSize < smallVmMinAppSize) {
					smallVmMinAppSize = vmAppSize;
					smallVmMaxLoad = vmLoad;
					smallVm = vm;
				}
				else if (vmAppSize == bigVmMinAppSize) {
					if (vmLoad > smallVmMaxLoad) {
						smallVmMaxLoad = vmLoad;
						smallVm = vm;
					}
				}
			}
		}
		
		if (null != bigVm)			// Found VM large enough to terminate stress situation and w/ minimum application size.
			return bigVm;
		else						// There was no large enough VM, so the largest one w/ minimum application size was selected.
			return smallVm;
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
	 * method ignores (or discards) Stressed Hosts and Hosts with currently
	 * invalid status.
	 */
	protected void classifyHosts(Collection<HostData> hosts, 
			ArrayList<HostData> partiallyUtilized, 
			ArrayList<HostData> underUtilized, 
			ArrayList<HostData> empty) {
		
		for (HostData host : hosts) {
			
			// Filter out Hosts with a currently invalid status.
			if (host.isStatusValid()) {
					
				double avgCpuUtilization = this.calculateHostAvgCpuUtilization(host);
				
				// Classify Hosts; ignore Stressed ones.
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
		
		for (VmStatus vm : vms) {
			
			// TODO: Accessing remote object (VM). Redesign mgmt. system to avoid this trick.
			
			switch (vm.getVm().getTaskInstance().getTask().getConstraintType()) {
			case INDEPENDENT:
				independent.add(vm);
				break;
			case ANTI_AFFINITY:
				antiAffinity.add(vm);
				break;
			case AFFINITY:
				affinity.add(vm);
				break;
			default:
				break;
			}
		}
	}
	
	private VmStatus findHostingVm(InteractiveTask task, ArrayList<VmStatus> vms) {
		
		for (VmStatus vm : vms) {
			
			// TODO: Accessing remote object (VM). Redesign mgmt. system to avoid this trick.
			
			if (vm.getVm().getTaskInstance().getTask().getId() == task.getId())
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
			
			// TODO: Accessing remote object (VM). Redesign mgmt. system to avoid this trick.
			
			InteractiveTask vmTask = (InteractiveTask) vm.getVm().getTaskInstance().getTask();
			ArrayList<InteractiveTask> affinitySet = ((InteractiveApplication) vmTask.getApplication()).getAffinitySet(vmTask);
			
			// Build the set of VMs hosting the Tasks in the previously found Affinity-set.
			ArrayList<VmStatus> affinitySetVms = new ArrayList<VmStatus>();
			for (InteractiveTask task : affinitySet) {
				affinitySetVms.add(this.findHostingVm(task, copy));
			}
			
			affinitySets.add(affinitySetVms);
			copy.removeAll(affinitySetVms);
		}
		
		return affinitySets;
	}
	
	/**
	 * Determines whether the given Host is hosting an instance of the given Task.
	 */
	private boolean isHostingTask(Task task, HostData host) {
		
		for (VmStatus vm : host.getCurrentStatus().getVms()) {
			
			// TODO: Accessing remote object (VM). Redesign mgmt. system to avoid this trick.
			
			if (vm.getVm().getTaskInstance().getTask().getId() == task.getId())
				return true;
		}
		
		return false;
	}
	
	/**
	 * Determines if the Host is Stressed or not, based on its average
	 * CPU utilization over the last window of time.
	 */
	protected boolean isStressed(HostData host) {
		if (this.calculateHostAvgCpuUtilization(host) >= upperThreshold)
			return true;
		
		return false;
	}
	
	/**
	 * Sorts the relocation candidates in increasing order by <CPU load>, previously removing
	 * from consideration those VMs with less CPU load than the CPU load by which the host is stressed.
	 */
	protected ArrayList<VmStatus> orderSourceVms(ArrayList<VmStatus> sourceVms, HostData source) {
		ArrayList<VmStatus> sorted = new ArrayList<VmStatus>();
		
		// Remove VMs with less CPU load than the CPU load by which the source 
		// host is stressed.
		double cpuExcess = source.getSandboxStatus().getResourcesInUse().getCpu() - source.getHostDescription().getResourceCapacity().getCpu() * this.upperThreshold;
		for (VmStatus vm : sourceVms)
			if (vm.getResourcesInUse().getCpu() >= cpuExcess)
				sorted.add(vm);
		
		if (!sorted.isEmpty())
			// Sort VMs in increasing order by CPU load.
			Collections.sort(sorted, VmStatusComparator.getComparator(VmStatusComparator.CPU_IN_USE));
		else {
			// Add original list of VMs and sort them in decreasing order by 
			// CPU load, so as to avoid trying to migrate the smallest VMs 
			// first (which would not help resolve the stress situation).
			sorted.addAll(sourceVms);
			Collections.sort(sorted, VmStatusComparator.getComparator(VmStatusComparator.CPU_IN_USE));
			Collections.reverse(sorted);
		}
		
		return sorted;
	}
	
	/**
	 * Sorts Partially-Utilized hosts in increasing order by <CPU utilization, power efficiency>,
	 * Underutilized hosts in decreasing order by <CPU utilization, power efficiency>, and Empty hosts
	 * in decreasing order by <power efficiency, power state>.
	 * 
	 * Returns Partially-utilized, Underutilized, and Empty hosts, in that order.
	 */
	protected ArrayList<HostData> orderTargetHosts(ArrayList<HostData> partiallyUtilized, ArrayList<HostData> underUtilized, ArrayList<HostData> empty) {
		ArrayList<HostData> targets = new ArrayList<HostData>();
		
		// Sort Partially-Utilized hosts in increasing order by <CPU utilization, power efficiency>.
		Collections.sort(partiallyUtilized, HostDataComparator.getComparator(HostDataComparator.CPU_UTIL, HostDataComparator.EFFICIENCY));
		
		// Sort Underutilized hosts in decreasing order by <CPU utilization, power efficiency>.
		Collections.sort(underUtilized, HostDataComparator.getComparator(HostDataComparator.CPU_UTIL, HostDataComparator.EFFICIENCY));
		Collections.reverse(underUtilized);
		
		// Sort Empty hosts in decreasing order by <power efficiency, power state>.
		Collections.sort(empty, HostDataComparator.getComparator(HostDataComparator.EFFICIENCY, HostDataComparator.PWR_STATE));
		Collections.reverse(empty);
		
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		targets.addAll(empty);
		
		return targets;
	}
	
	protected HostData placeVmWherever(VmStatus request, Collection<HostData> targets) {
		
		Resources reqResources = new Resources();
		reqResources.setCpu(request.getCpu());
		reqResources.setMemory(request.getMemory());
		reqResources.setBandwidth(request.getBandwidth());
		reqResources.setStorage(request.getStorage());
		
		for (HostData target : targets) {
			
			// Check that target Host is capable and has enough capacity left to host the VM, 
			// and also that it will not exceed the target utilization.
			if (HostData.canHost(request.getVMDescription().getCores(), request.getVMDescription().getCoreCapacity(), reqResources, target.getSandboxStatus(), target.getHostDescription()) &&
				(target.getSandboxStatus().getResourcesInUse().getCpu() + request.getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
				
				// Add a dummy placeholder VM to keep track of placed VM resource requirements.
				target.getSandboxStatus().instantiateVm(new VmStatus(request.getVMDescription().getCores(),	request.getVMDescription().getCoreCapacity(), reqResources));
				
				return new InstantiateVmAction(target, request, event);
			}
		}
		
		return null;
	}
	
	protected Map<Integer, HostData> placeVmsApart(ArrayList<VmStatus> antiAffinitySet,	Collection<HostData> targets) {
		ArrayList<InstantiateVmAction> actions = new ArrayList<InstantiateVmAction>();
		
		// Create copy of target hosts' list for manipulation.
		Collection<HostData> hosts = new ArrayList<HostData>(targets);
		
		for (VmAllocationRequest request : antiAffinitySet) {
			
			Resources reqResources = new Resources();
			reqResources.setCpu(request.getCpu());
			reqResources.setMemory(request.getMemory());
			reqResources.setBandwidth(request.getBandwidth());
			reqResources.setStorage(request.getStorage());
			
			HostData targetHost = null;
			for (HostData target : hosts) {
				
				// Check that target Host is capable and has enough capacity left to host the VM, 
				// and also that it will not exceed the target utilization.
				if (HostData.canHost(request.getVMDescription().getCores(), request.getVMDescription().getCoreCapacity(), reqResources, target.getSandboxStatus(), target.getHostDescription()) &&
					(target.getSandboxStatus().getResourcesInUse().getCpu() + request.getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
					
					targetHost = target;
					
					// Add a dummy placeholder VM to keep track of placed VM resource requirements.
					target.getSandboxStatus().instantiateVm(new VmStatus(request.getVMDescription().getCores(),	request.getVMDescription().getCoreCapacity(), reqResources));
					
					break;
				}
			}
			
			if (null != targetHost) {
				actions.add(new InstantiateVmAction(targetHost, request, event));
				// Remove host from target hosts' list, so that it is not considered again here.
				hosts.remove(targetHost);
			}
			else
				return null;
		}
		
		assert antiAffinitySet.size() == actions.size();
		assert targets.size() == hosts.size() + actions.size();
		
		return actions;
	}
	
	protected Map<Integer, HostData> placeVmsTogether(ArrayList<VmStatus> affinitySet, Collection<HostData> targets) {
		ArrayList<InstantiateVmAction> actions = new ArrayList<InstantiateVmAction>();
		
		int maxReqCores = 0;
		int maxReqCoreCapacity = 0;
		int totalCpu = 0;
		int totalMemory = 0;
		int totalBandwidth = 0;
		int totalStorage = 0;
		for (VmAllocationRequest request : affinitySet) {
			if (request.getVMDescription().getCores() > maxReqCores)
				maxReqCores = request.getVMDescription().getCores();
			
			if (request.getVMDescription().getCoreCapacity() > maxReqCoreCapacity)
				maxReqCoreCapacity = request.getVMDescription().getCoreCapacity();
			
			totalCpu += request.getCpu();
			totalMemory += request.getMemory();
			totalBandwidth += request.getBandwidth();
			totalStorage += request.getStorage();
		}
		Resources totalReqResources = new Resources(totalCpu, totalMemory, totalBandwidth, totalStorage);
		
		for (HostData target : targets) {
			
			// Check that target Host is capable and has enough capacity left to host the VM, 
			// and also that it will not exceed the target utilization.
			if (HostData.canHost(maxReqCores, maxReqCoreCapacity, totalReqResources, target.getSandboxStatus(), target.getHostDescription()) &&
				(target.getSandboxStatus().getResourcesInUse().getCpu() + totalCpu) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
				
				for (VmAllocationRequest request : affinitySet) {
					
					Resources reqResources = new Resources();
					reqResources.setCpu(request.getCpu());
					reqResources.setMemory(request.getMemory());
					reqResources.setBandwidth(request.getBandwidth());
					reqResources.setStorage(request.getStorage());
					
					// Add dummy placeholder VM to keep track of placed VM' resource requirements.
					target.getSandboxStatus().instantiateVm(new VmStatus(request.getVMDescription().getCores(),	request.getVMDescription().getCoreCapacity(), reqResources));
					
					actions.add(new InstantiateVmAction(target, request, event));
				}
				
				return actions;
			}
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
