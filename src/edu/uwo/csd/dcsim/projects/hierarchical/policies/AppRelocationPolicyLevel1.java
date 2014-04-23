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
import edu.uwo.csd.dcsim.common.Tuple;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.HostDataComparator;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.VmStatusComparator;
import edu.uwo.csd.dcsim.management.action.MigrationAction;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.management.events.MigrationCompletedEvent;
import edu.uwo.csd.dcsim.projects.centralized.events.StressCheckEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.AppStatus;
import edu.uwo.csd.dcsim.projects.hierarchical.MigRequestEntry;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.MigRequestRecord;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.RackManager;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.VmMigrationsManager;
import edu.uwo.csd.dcsim.projects.hierarchical.events.AppMigAcceptEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.AppMigRequestEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.AppMigRejectEvent;

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
		addRequiredCapability(VmMigrationsManager.class);
		
		this.target = target;
		
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}
	
	/**
	 * This event can only come from another Rack Manager with information about the selected target Host.
	 */
	public void execute(AppMigAcceptEvent event) {
		
		simulation.getLogger().debug("[Rack #" + manager.getCapability(RackManager.class).getRack().getId() + "]"
				+ " AppRelocationPolicyLevel1 - MigRequest accepted - App #" + event.getApplication().getId());
		
		// Get entry (and source Hosts) from migration requests record.
		MigRequestEntry entry = manager.getCapability(MigRequestRecord.class).getEntry(event.getApplication(), manager);
		Map<Integer, HostData> sourceHostMap = entry.getSourceHosts();
		
		// Get target Hosts from event.
		Map<Integer, Host> targetHostMap = event.getTargetHosts();
		
		for (Map.Entry<Integer, HostData> pair : sourceHostMap.entrySet()) {
			// Invalidate source Host' status, as we know it to be incorrect until the next status update arrives.
			pair.getValue().invalidateStatus(simulation.getSimulationTime());
			// Trigger migration.
			new MigrationAction(pair.getValue().getHostManager(), pair.getValue().getHost(), targetHostMap.get(pair.getKey()), pair.getKey()).execute(simulation, this);
			
			simulation.getLogger().debug("[Rack #" + manager.getCapability(RackManager.class).getRack().getId() + "]"
					+ " AppRelocationPolicyLevel1 - Migrating VM #" + pair.getKey() + " from Host #" + pair.getValue().getId() + " to Host #" + targetHostMap.get(pair.getKey()).getId());
			
		}
		
		// Delete entry from migration requests record.
		manager.getCapability(MigRequestRecord.class).removeEntry(entry);
	}
	
	/**
	 * This event can only come from the DC Manager, signaling that nobody can accept the migration request.
	 */
	public void execute(AppMigRejectEvent event) {
		
		simulation.getLogger().debug("[Rack #" + manager.getCapability(RackManager.class).getRack().getId() + "]"
				+ " AppRelocationPolicyLevel1 - MigRequest rejected - App #" + event.getApplication().getId());
		
		// Delete entry from migration requests record.
		MigRequestRecord record = manager.getCapability(MigRequestRecord.class);
		MigRequestEntry entry = record.getEntry(event.getApplication(), event.getOrigin());
		record.removeEntry(entry);
		
		// The VMs had been marked for migration. Clear them.
		VmMigrationsManager ongoingMigs = manager.getCapability(VmMigrationsManager.class);
		for (VmStatus vm : entry.getApplication().getAllVms()) {
			ongoingMigs.removeMigratingVm(vm.getId());
		}
	}
	
	/**
	 * This event comes from the Cluster Manager, trying to migrate an application to this Rack.
	 */
	public void execute(AppMigRequestEvent event) {
		
		simulation.getLogger().debug("[Rack #" + manager.getCapability(RackManager.class).getRack().getId() + "]"
				+ " AppRelocationPolicyLevel1 - New MigRequest - App #" + event.getApplication().getId());
		
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
			
			simulation.getLogger().debug("[Rack #" + manager.getCapability(RackManager.class).getRack().getId() + "]"
					+ " AppRelocationPolicyLevel1 - ACCEPTED.");
			
			simulation.sendEvent(new AppMigAcceptEvent(event.getOrigin(), event.getApplication(), targets));
		}
		else {	// Otherwise, send message to ClusterManager rejecting the migration request.
			
			simulation.getLogger().debug("[Rack #" + manager.getCapability(RackManager.class).getRack().getId() + "]"
					+ " AppRelocationPolicyLevel1 - REJECTED.");
			
			simulation.sendEvent(new AppMigRejectEvent(target, event.getApplication(), event.getOrigin(), manager.getCapability(RackManager.class).getRack().getId()));
		}
	}
	
	public void execute(MigrationCompletedEvent event) {
		VmMigrationsManager ongoingMigs = manager.getCapability(VmMigrationsManager.class);
		
//		if (!ongoingMigs.removeMigratingVm(event.getVmId()))
//			throw new RuntimeException("Migration of VM #" + event.getVmId() + " completed, but the migration was NOT registered as actually happening.");
		
		ongoingMigs.removeMigratingVm(event.getVmId());
	}
	
	/**
	 * Performs a stress check on the Host indicated by the event. If the host is stressed
	 * and has no pending outgoing migrations, a Relocation process is started.
	 */
	public void execute(StressCheckEvent event) {
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		
		HostData host = hostPool.getHost(event.getHostId());
		if (this.isStressed(host) && host.getCurrentStatus().getOutgoingMigrationCount() == 0) {
			
			// Perform VM Relocation process within the scope of the Rack.
			// If it fails, request assistance from ClusterManager.
			if (!this.performInternalVmRelocation(event.getHostId())) {
				
				simulation.getLogger().debug("[Rack #" + manager.getCapability(RackManager.class).getRack().getId() + "]"
						+ " AppRelocationPolicyLevel1 - Internal Relocation process FAILED.");
				
				if (!this.performExternalVmRelocation(event.getHostId()))
					simulation.getLogger().debug("[Rack #" + manager.getCapability(RackManager.class).getRack().getId() + "]"
							+ " AppRelocationPolicyLevel1 - External Relocation process FAILED.");
			}
		}
	}
	
	/**
	 * Performs the Relocation process within the scope of the Rack. The process searches for
	 * a feasible VM migration with target Host inside the Rack.
	 */
	protected boolean performInternalVmRelocation(int hostId) {
		
		simulation.getLogger().debug("[Rack #" + manager.getCapability(RackManager.class).getRack().getId() + "]"
				+ " AppRelocationPolicyLevel1 - Internal Relocation process for Host #" + hostId);
		
		VmMigrationsManager ongoingMigs = manager.getCapability(VmMigrationsManager.class);
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
		
		// Classify source Host's VMs according to the constraint-type of their hosted Task instance.
		ArrayList<VmStatus> independent = new ArrayList<VmStatus>();
		ArrayList<VmStatus> antiAffinity = new ArrayList<VmStatus>();
		ArrayList<VmStatus> affinity = new ArrayList<VmStatus>();
		this.classifyVms(source.getCurrentStatus().getVms(), independent, antiAffinity, affinity);
		
		// Process Independent VMs.
		
		// Sort VMs, removing small VMs from consideration for migration.
		independent = this.orderSourceVms(independent, source);
		
		MigrationAction mig = null;
		for (VmStatus vm : independent) {
			
			// Skip VMs migrating out or scheduled to do so.
			if (ongoingMigs.isMigrating(vm.getId()))
				continue;
			
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
					
					simulation.getLogger().debug("[Rack #" + manager.getCapability(RackManager.class).getRack().getId() + "]"
							+ " AppRelocationPolicyLevel1 - Migrating (Independent) VM #" + vm.getId() + " from Host #" + source.getId() + " to Host #" + target.getId());
					
					break;		// Found VM migration. Exit loop.
				}
			}
			
			if (null != mig)	// Found VM migration. Exit loop.
				break;
		}
		
		if (null != mig) {		// Trigger migration and exit.
			ongoingMigs.addMigratingVm(mig.getVmId());
			mig.execute(simulation, this);
			return true;
		}
		
		// Process Anti-affinity VMs.
		
		// Sort VMs, removing small VMs from consideration for migration.
		antiAffinity = this.orderSourceVms(antiAffinity, source);
		
		for (VmStatus vm : antiAffinity) {
			
			// Skip VMs migrating out or scheduled to do so.
			if (ongoingMigs.isMigrating(vm.getId()))
				continue;
			
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
					
					simulation.getLogger().debug("[Rack #" + manager.getCapability(RackManager.class).getRack().getId() + "]"
							+ " AppRelocationPolicyLevel1 - Migrating (Anti-affinity) VM #" + vm.getId() + " from Host #" + source.getId() + " to Host #" + target.getId());
					
					break;		// Found VM migration. Exit loop.
				}
			}
			
			if (null != mig)	// Found VM migration. Exit loop.
				break;
		}
		
		if (null != mig) {		// Trigger migration and exit.
			ongoingMigs.addMigratingVm(mig.getVmId());
			mig.execute(simulation, this);
			return true;
		}
		
		// Process Affinity VMs.
		ArrayList<MigrationAction> migActions = new ArrayList<MigrationAction>();
		
		ArrayList<ArrayList<VmStatus>> affinitySets = this.groupVmsByAffinity(affinity);
		
		// If there's more than one Affinity-set, sort them in increasing order by size.
		
		// TODO: Shouldn't we also consider the total CPU load of each Affinity set when sorting? Otherwise, we may migrate a set of VMs with not enough load to terminate the stress situation.
		
		if (affinitySets.size() > 1)
			Collections.sort(affinitySets, new Comparator<List<?>>(){
				@Override
				public int compare(List<?> arg0, List<?> arg1) {
					return arg0.size() - arg1.size();
				}
			});
		
		for (ArrayList<VmStatus> affinitySet : affinitySets) {
			
			// Skip VMs migrating out or scheduled to do so.
			// Since these VMs are constrained by affinity, if one VM is migration, we know that the others in the affinity-set are, too.
			if (!affinitySet.isEmpty() && ongoingMigs.isMigrating(affinitySet.get(0).getId()))
				continue;
			
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
						
						simulation.getLogger().debug("[Rack #" + manager.getCapability(RackManager.class).getRack().getId() + "]"
								+ " AppRelocationPolicyLevel1 - Migrating (Affinity) VM #" + v.getId() + " from Host #" + source.getId() + " to Host #" + target.getId());
						
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
			for (MigrationAction action : migActions) {
				ongoingMigs.addMigratingVm(action.getVmId());
				action.execute(simulation, this);
			}
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
	protected boolean performExternalVmRelocation(int hostId) {
		
		simulation.getLogger().debug("[Rack #" + manager.getCapability(RackManager.class).getRack().getId() + "]"
				+ " AppRelocationPolicyLevel1 - External Relocation process for Host #" + hostId);
		
		VmMigrationsManager ongoingMigs = manager.getCapability(VmMigrationsManager.class);
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		
		// Find Application to migrate away.
		AppStatus application = this.findCandidateApp(hostPool.getHost(hostId));
		if (null == application)
			return false;
		
		simulation.getLogger().debug("[Rack #" + manager.getCapability(RackManager.class).getRack().getId() + "]"
				+ " AppRelocationPolicyLevel1 - Trying to migrate away App #" + application.getId() + " (#vms = " + application.getAllVms().size() + ")");
		
		// Build source Hosts map.
		// TODO: This could be something built in a Rack capability or something.
		Map<Integer, HostData> vmHostMap = new HashMap<Integer, HostData>();
		for (HostData host : hostPool.getHosts()) {
			for (VmStatus vm : host.getCurrentStatus().getVms()) {
				if (!vmHostMap.containsKey(vm.getId()))
					vmHostMap.put(vm.getId(), host);
				else {
					// The VM already exists in the map (i.e., another Host has already claimed ownership).
					// If the current Host has a newer timestamp, then we assume his claim to be correct.
					if (host.getCurrentStatus().getTimeStamp() > vmHostMap.get(vm.getId()).getCurrentStatus().getTimeStamp())
						vmHostMap.put(vm.getId(), host);
				}
			}
		}
		
		Map<Integer, HostData> sourceHostMap = new HashMap<Integer, HostData>();
		for (VmStatus vm : application.getAllVms()) {
			sourceHostMap.put(vm.getId(), vmHostMap.get(vm.getId()));
			
			// Mark VMs as scheduled for migration.
			ongoingMigs.addMigratingVm(vm.getId());
		}
		
		// Request assistance from ClusterManager to find a target Rack to which to migrate the selected application.
		simulation.sendEvent(new AppMigRequestEvent(target, application, manager, manager.getCapability(RackManager.class).getRack().getId()));
		
		// Keep track of the migration request just sent.
		manager.getCapability(MigRequestRecord.class).addEntry(new MigRequestEntry(application, manager, sourceHostMap));
		
		return true;
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
	 * Returns an Application that can be migrated away from the Rack containing the given Host.
	 * 
	 * The method returns null if all the VMs in the given Host are migrating out or are scheduled to do so,
	 * or the Applications associated to the hosted VMs are not available for migration.
	 */
	protected AppStatus findCandidateApp(HostData source) {
		
		// Determine the amount of CPU load to get rid of to bring the Host back to its target utilization.
		//double cpuExcess = source.getCurrentStatus().getResourcesInUse().getCpu() - source.getHostDescription().getResourceCapacity().getCpu() * this.targetUtilization;
		double cpuExcess = (this.calculateHostAvgCpuUtilization(source) - this.targetUtilization) * source.getHostDescription().getResourceCapacity().getCpu();
		
		// Classify VMs according to their CPU load.
		ArrayList<VmStatus> bigVmList = new ArrayList<VmStatus>();
		ArrayList<VmStatus> smallVmList = new ArrayList<VmStatus>();
		for (VmStatus vm : source.getCurrentStatus().getVms()) {
			if (vm.getResourcesInUse().getCpu() >= cpuExcess)
				bigVmList.add(vm);
			else
				smallVmList.add(vm);
		}
		
		// Process VMs with higher load.
		
		// Weed out applications with VMs migrating out or scheduled to do so.
		ArrayList<Tuple<VmStatus, AppStatus>> vmAppList = new ArrayList<Tuple<VmStatus, AppStatus>>();
		for (VmStatus vm : bigVmList) {
			
			// TODO: Accessing remote object (VM). Redesign mgmt. system to avoid this trick.
			
			AppStatus app = new AppStatus((InteractiveApplication) vm.getVm().getTaskInstance().getTask().getApplication());
			if (this.canMigrate(app))
				vmAppList.add(new Tuple<VmStatus, AppStatus>(vm, app));
		}
		
		// Find candidate application: has the smallest size and its associated VM has the smallest load.
		Tuple<VmStatus, AppStatus> candidate = null;
		int minAppSize = Integer.MAX_VALUE;
		int minLoad = Integer.MAX_VALUE;
		for (Tuple<VmStatus, AppStatus> tuple : vmAppList) {
			int appSize = tuple.b.getAllVms().size();
			int vmLoad = tuple.a.getResourcesInUse().getCpu();
			if (appSize < minAppSize) {
				minAppSize = appSize;
				minLoad = vmLoad;
				candidate = tuple;
			}
			else if (appSize == minAppSize && vmLoad < minLoad) {
				minLoad = vmLoad;
				candidate = tuple;
			}
		}
		
		if (null != candidate)
			return candidate.b;
		
		// Process VMs with lower load.
		
		// Weed out applications with VMs migrating out or scheduled to do so.
		vmAppList = new ArrayList<Tuple<VmStatus, AppStatus>>();
		for (VmStatus vm : smallVmList) {
			
			// TODO: Accessing remote object (VM). Redesign mgmt. system to avoid this trick.
			
			AppStatus app = new AppStatus((InteractiveApplication) vm.getVm().getTaskInstance().getTask().getApplication());
			if (this.canMigrate(app))
				vmAppList.add(new Tuple<VmStatus, AppStatus>(vm, app));
		}
		
		// Find candidate application: has the smallest size and its associated VM has the largest load.
		candidate = null;
		minAppSize = Integer.MAX_VALUE;
		int maxLoad = Integer.MAX_VALUE;
		for (Tuple<VmStatus, AppStatus> tuple : vmAppList) {
			int appSize = tuple.b.getAllVms().size();
			int vmLoad = tuple.a.getResourcesInUse().getCpu();
			if (appSize < minAppSize) {
				minAppSize = appSize;
				maxLoad = vmLoad;
				candidate = tuple;
			}
			else if (appSize == minAppSize && vmLoad > maxLoad) {
				maxLoad = vmLoad;
				candidate = tuple;
			}
		}
		
		return (null != candidate)? candidate.b : null;
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
	 * Determines whether an Application is available for migration or not.
	 * 
	 * It returns true if all the VMs that compose the application are not migrating out
	 * or scheduled to do so. Otherwise, it returns false.
	 */
	private boolean canMigrate(AppStatus application) {
		VmMigrationsManager ongoingMigs = manager.getCapability(VmMigrationsManager.class);
		
		for (VmStatus vm : application.getAllVms()) {
			if (ongoingMigs.isMigrating(vm.getId()))
				return false;
		}
		
		return true;
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
			}
		}
	}
	
	private VmStatus findHostingVm(InteractiveTask task, ArrayList<VmStatus> vms) {
		
		for (VmStatus vm : vms) {
			
			// TODO: Accessing remote object (VM). Redesign mgmt. system to avoid this trick.
			
			InteractiveTask vmTask = (InteractiveTask) vm.getVm().getTaskInstance().getTask();
			if (vmTask.getId() == task.getId() && vmTask.getApplication().getId() == task.getApplication().getId())
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
//				affinitySetVms.add(this.findHostingVm(task, copy));
				VmStatus hostingVm = this.findHostingVm(task, copy);
				if (null != hostingVm)
					affinitySetVms.add(hostingVm);
				else
					throw new RuntimeException("Failed to find VM hosting instance of Task #" + task.getId() + " from App #" + task.getApplication().getId());
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
		
		for (VmStatus vm : host.getSandboxStatus().getVms()) {
			
			// TODO: Accessing remote object (VM). Redesign mgmt. system to avoid this trick.
			
			InteractiveTask vmTask = (InteractiveTask) vm.getVm().getTaskInstance().getTask();
			if (vmTask.getId() == task.getId() && vmTask.getApplication().getId() == task.getApplication().getId())
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
		//double cpuExcess = source.getSandboxStatus().getResourcesInUse().getCpu() - source.getHostDescription().getResourceCapacity().getCpu() * this.upperThreshold;
		double cpuExcess = (this.calculateHostAvgCpuUtilization(source) - this.targetUtilization) * source.getHostDescription().getResourceCapacity().getCpu();
		
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
	
	protected HostData placeVmWherever(VmStatus vm, Collection<HostData> targets) {
		
		for (HostData target : targets) {
			
			// Check that target Host is capable and has enough capacity left to host the VM, 
			// and also that it will not exceed the target utilization.
			if (HostData.canHost(vm, target.getSandboxStatus(), target.getHostDescription()) &&
				(target.getSandboxStatus().getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
				
				// Add a dummy placeholder VM to keep track of placed VM resource requirements.
				target.getSandboxStatus().instantiateVm(vm);
				
				return target;
			}
		}
		
		return null;
	}
	
	protected Map<Integer, HostData> placeVmsApart(ArrayList<VmStatus> antiAffinitySet, Collection<HostData> targets) {
		Map<Integer, HostData> mapping = new HashMap<Integer, HostData>();
		
		// Create copy of target hosts' list for manipulation.
		Collection<HostData> hosts = new ArrayList<HostData>(targets);
		
		for (VmStatus vm : antiAffinitySet) {
			
			HostData targetHost = null;
			for (HostData target : hosts) {
				
				// Check that target Host is capable and has enough capacity left to host the VM, 
				// and also that it will not exceed the target utilization.
				if (HostData.canHost(vm, target.getSandboxStatus(), target.getHostDescription()) &&
					(target.getSandboxStatus().getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
					
					targetHost = target;
					
					// Add a dummy placeholder VM to keep track of placed VM resource requirements.
					target.getSandboxStatus().instantiateVm(vm);
					
					break;
				}
			}
			
			if (null != targetHost) {
				mapping.put(vm.getId(), targetHost);
				// Remove host from target hosts' list, so that it is not considered again here.
				hosts.remove(targetHost);
			}
			else
				return null;
		}
		
		assert antiAffinitySet.size() == mapping.size();
		assert targets.size() == hosts.size() + mapping.size();
		
		return mapping;
	}
	
	protected Map<Integer, HostData> placeVmsTogether(ArrayList<VmStatus> affinitySet, Collection<HostData> targets) {
		Map<Integer, HostData> mapping = new HashMap<Integer, HostData>();
		
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
			
			// Check that target Host is capable and has enough capacity left to host the VM, 
			// and also that it will not exceed the target utilization.
			if (HostData.canHost(maxReqCores, maxReqCoreCapacity, totalReqResources, target.getSandboxStatus(), target.getHostDescription()) &&
				(target.getSandboxStatus().getResourcesInUse().getCpu() + totalReqResources.getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
				
				for (VmStatus vm : affinitySet) {
					// Add dummy placeholder VM to keep track of placed VM' resource requirements.
					target.getSandboxStatus().instantiateVm(vm);
					
					mapping.put(vm.getId(), target);
				}
				
				return mapping;
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
