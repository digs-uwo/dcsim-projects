package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uwo.csd.dcsim.application.InteractiveTask;
import edu.uwo.csd.dcsim.application.Task;
import edu.uwo.csd.dcsim.application.Task.TaskConstraintType;
import edu.uwo.csd.dcsim.common.SimTime;
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
import edu.uwo.csd.dcsim.management.action.ConcurrentManagementActionExecutor;
import edu.uwo.csd.dcsim.management.action.MigrationAction;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.projects.centralized.events.StressCheckEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.AppData;
import edu.uwo.csd.dcsim.projects.hierarchical.AppStatus;
import edu.uwo.csd.dcsim.projects.hierarchical.MigRequestEntry;
import edu.uwo.csd.dcsim.projects.hierarchical.TaskData;
import edu.uwo.csd.dcsim.projects.hierarchical.VmData;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.AppPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.MigRequestRecord;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.RackManager;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.MigrationTrackingManager;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.VmPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.events.AppMigAcceptEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.AppMigRequestEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.AppMigRejectEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.IncomingMigrationEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.RepairBrokenAppEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.SurrogateAppDataEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.SurrogateAppMigrateEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.SurrogateAppRejectEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.SurrogateAppRequestEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.VmMigAcceptEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.VmMigRejectEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.events.VmMigRequestEvent;

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
public class RelocationPolicyLevel1 extends Policy {
	
	protected AutonomicManager target;
	
	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	
	/**
	 * Creates an instance of AppRelocationPolicyLevel1.
	 */
	public RelocationPolicyLevel1(AutonomicManager target, double lowerThreshold, double upperThreshold, double targetUtilization) {
		addRequiredCapability(AppPoolManager.class);
		addRequiredCapability(HostPoolManager.class);
		addRequiredCapability(VmPoolManager.class);
		addRequiredCapability(MigRequestRecord.class);
		addRequiredCapability(MigrationTrackingManager.class);
		
		this.target = target;
		
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}
	
	/**
	 * 
	 * General events.
	 * 
	 */
	
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
				
				simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - Internal Relocation process FAILED.",
						manager.getCapability(RackManager.class).getRack().getId()));
				
				if (!this.performExternalVmRelocation(event.getHostId()))
					simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - External Relocation process FAILED.",
							manager.getCapability(RackManager.class).getRack().getId()));
			}
		}
	}
	
	public void execute(RepairBrokenAppEvent event) {
		
		AppData app = manager.getCapability(AppPoolManager.class).getApplication(event.getAppId());
		
		if (app.getSurrogates() != null) {		// The application still has 1 or more VMs away.
			
			simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - Trying to recover %d surrogate(s) for App #%d.",
					manager.getCapability(RackManager.class).getRack().getId(),
					app.getSurrogates().size(),
					app.getId()));
			
			for (AutonomicManager remote : app.getSurrogates()) {
				
				// Contact remote Rack to recover (the VM belonging to) the application appId.
				simulation.sendEvent(new SurrogateAppRequestEvent(remote, app.getId()));
			}
			
			// Set next periodic check to verify if the procedure was successful, or repeat otherwise.
			// TODO The _delay_ determines the length of the period. This value should be set in a file, or perhaps by parameter.
			long delay = SimTime.hours(1);
			simulation.sendEvent(new RepairBrokenAppEvent(manager, app.getId()), simulation.getSimulationTime() + delay);
		}
	}
	
	/**
	 * 
	 * Events to negotiate the migration of an application.
	 * 
	 */
	
	/**
	 * This event comes from the Cluster Manager, trying to migrate an application to this Rack.
	 */
	public void execute(AppMigRequestEvent event) {
		
		simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - New MigRequest - App #%d.",
				manager.getCapability(RackManager.class).getRack().getId(),
				event.getApplication().getId()));
		
		// Find target Hosts in this Rack for the VMs that compose the migrating application.
		Map<Integer, HostData> targetsMap = this.findMigrationTargets(event.getApplication());
		
		// If found, send message to RackManager origin accepting the migration request.
		if (null != targetsMap) {
			// Build map of target Hosts: < vmId , Host > .
			Map<Integer, Host> targets = new HashMap<Integer, Host>();
			for (Map.Entry<Integer, HostData> entry : targetsMap.entrySet()) {
				
				targets.put(entry.getKey(), entry.getValue().getHost());
				
				// Invalidate target Hosts' status, as we know them to be incorrect until the next status updates arrive.
				entry.getValue().invalidateStatus(simulation.getSimulationTime());
			}
			
			simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - ACCEPTED.",
					manager.getCapability(RackManager.class).getRack().getId()));
			
			simulation.sendEvent(new AppMigAcceptEvent(event.getOrigin(), event.getApplication(), targets, manager));
		}
		else {	// Otherwise, send message to ClusterManager rejecting the migration request.
			
			simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - REJECTED.",
					manager.getCapability(RackManager.class).getRack().getId()));
			
			simulation.sendEvent(new AppMigRejectEvent(target, event.getApplication(), event.getOrigin(), manager.getCapability(RackManager.class).getRack().getId()));
		}
	}
	
	/**
	 * This event can only come from another Rack Manager with information about the selected target Hosts.
	 */
	public void execute(AppMigAcceptEvent event) {
		
		simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - MigRequest accepted - App #%d." ,
				manager.getCapability(RackManager.class).getRack().getId(),
				event.getApplication().getId()));
		
		// Get entry from migration requests record.
		MigRequestEntry entry = manager.getCapability(MigRequestRecord.class).getEntry(event.getApplication(), manager);
		if (null == entry)
			throw new RuntimeException(String.format("Received a migration request acceptance for App #%d, but there is no record of such a request being made.", event.getApplication().getId()));
		
		// Get target Hosts from event.
		Map<Integer, Host> targetHostMap = event.getTargetHosts();
		
		VmPoolManager vmPool = manager.getCapability(VmPoolManager.class);
		// TODO: Migrations are issued concurrently. Should they be issued sequentially instead?
		ConcurrentManagementActionExecutor migrations = new ConcurrentManagementActionExecutor();
		for (VmStatus vm : entry.getApplication().getAllVms()) {
			
			HostData source = vmPool.getVm(vm.getId()).getHost();
			
			// Invalidate source Host' status, as we know it to be incorrect until the next status update arrives.
			source.invalidateStatus(simulation.getSimulationTime());
			
			migrations.addAction(new MigrationAction(source.getHostManager(), source.getHost(), targetHostMap.get(vm.getId()), vm.getId()));
			
			simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - Migrating VM #%d from Host #%d to Host #%d.",
					manager.getCapability(RackManager.class).getRack().getId(),
					vm.getId(),
					source.getId(),
					targetHostMap.get(vm.getId()).getId()));
			
		}
		
		// Send AppData and VmData information to target Rack.
		AppData application = manager.getCapability(AppPoolManager.class).getApplication(event.getApplication().getId());
		simulation.sendEvent(new IncomingMigrationEvent(event.getOrigin(), application, vmPool.getVms(application.getHostingVmsIds()), targetHostMap, manager));
		
		// Trigger migrations.
		migrations.execute(simulation, this);
		
		// Delete entry from migration requests record.
		manager.getCapability(MigRequestRecord.class).removeEntry(entry);
	}
	
	/**
	 * This event can only come from the DC Manager, signaling that nobody can accept the migration request.
	 */
	public void execute(AppMigRejectEvent event) {
		
		simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - MigRequest rejected - App #%d.",
				manager.getCapability(RackManager.class).getRack().getId(),
				event.getApplication().getId()));
		
		// Delete entry from migration requests record.
		MigRequestRecord record = manager.getCapability(MigRequestRecord.class);
		MigRequestEntry entry = record.getEntry(event.getApplication(), event.getOrigin());
		record.removeEntry(entry);
		
		// The application's VMs had been marked for migration. Clear them.
		MigrationTrackingManager ongoingMigs = manager.getCapability(MigrationTrackingManager.class);
		for (VmStatus vm : entry.getApplication().getAllVms()) {
			ongoingMigs.removeMigratingVm(vm.getId());
		}
	}
	
	/**
	 * 
	 * Events to negotiate the migration of a VM.
	 * 
	 */
	
	/**
	 * This event comes from the Cluster Manager, trying to migrate a VM to this Rack.
	 */
	public void execute(VmMigRequestEvent event) {
		
		simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - New MigRequest - VM #%d.",
				manager.getCapability(RackManager.class).getRack().getId(),
				event.getVm().getId()));
		
		// Find a target Host in the Rack.
		HostData targetHost = this.findMigrationTarget(event.getVm());
		
		// If found, send message to RackManager origin accepting the migration request.
		if (null != targetHost) {
			// Invalidate target Host' status, as we know it to be incorrect until the next status update arrives.
			targetHost.invalidateStatus(simulation.getSimulationTime());
			
			simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - ACCEPTED.",
					manager.getCapability(RackManager.class).getRack().getId()));
			
			simulation.sendEvent(new VmMigAcceptEvent(event.getOrigin(), event.getVm(), targetHost.getHost(), manager));
		}
		else {	// Otherwise, send message to ClusterManager rejecting the migration request.
			
			simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - REJECTED.",
					manager.getCapability(RackManager.class).getRack().getId()));
			
			simulation.sendEvent(new VmMigRejectEvent(target, event.getVm(), event.getOrigin(), manager.getCapability(RackManager.class).getRack().getId()));
		}
	}
	
	/**
	 * This event can only come from another Rack Manager with information about the selected target Host.
	 */
	public void execute(VmMigAcceptEvent event) {
		
		simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - MigRequest accepted - VM #%d." ,
				manager.getCapability(RackManager.class).getRack().getId(),
				event.getVm().getId()));
		
		// Get entry from migration requests record.
		MigRequestEntry entry = manager.getCapability(MigRequestRecord.class).getEntry(event.getVm(), manager);
		if (null == entry)
			throw new RuntimeException(String.format("Received a migration request acceptance for VM #%d, but there is no record of such a request being made.", event.getVm().getId()));
		
		VmData vmData = manager.getCapability(VmPoolManager.class).getVm(event.getVm().getId());
		HostData source = vmData.getHost();
		
		// Invalidate source Host' status, as we know it to be incorrect until the next status update arrives.
		source.invalidateStatus(simulation.getSimulationTime());
		
		// Send AppData (surrogate) and VmData information to target Rack.
		TaskData task = vmData.getTask();
		AppData surrogate = manager.getCapability(AppPoolManager.class).getApplication(task.getAppId()).createSurrogate(task, event.getOrigin());
		Collection<VmData> vms = new ArrayList<VmData>();
		vms.add(vmData);
		Map<Integer, Host> targetHostMap = new HashMap<Integer, Host>();
		targetHostMap.put(vmData.getId(), event.getTargetHost());
		simulation.sendEvent(new IncomingMigrationEvent(event.getOrigin(), surrogate, vms, targetHostMap, manager));
		
		simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - Migrating VM #%d from Host #%d to Host #%d.",
				manager.getCapability(RackManager.class).getRack().getId(),
				vmData.getId(),
				source.getId(),
				event.getTargetHost().getId()));
		
		// Trigger migration.
		new MigrationAction(source.getHostManager(), source.getHost(), event.getTargetHost(), vmData.getId()).execute(simulation, this);
		
		// Set periodic check to bring back the VM.
		// TODO The _delay_ determines the length of the period. This value should be set in a file, or perhaps by parameter.
		long delay = SimTime.hours(1);
		simulation.sendEvent(new RepairBrokenAppEvent(manager, surrogate.getId()), simulation.getSimulationTime() + delay);
		
		// Delete entry from migration requests record.
		manager.getCapability(MigRequestRecord.class).removeEntry(entry);
	}
	
	/**
	 * This event can only come from the DC Manager, signaling that nobody can accept the migration request.
	 */
	public void execute(VmMigRejectEvent event) {
		
		simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - MigRequest rejected - VM #%d.",
				manager.getCapability(RackManager.class).getRack().getId(),
				event.getVm().getId()));
		
		// Delete entry from migration requests record.
		MigRequestRecord record = manager.getCapability(MigRequestRecord.class);
		record.removeEntry(record.getEntry(event.getVm(), event.getOrigin()));
		
		// The VM had been marked for migration. Clear it.
		manager.getCapability(MigrationTrackingManager.class).removeMigratingVm(event.getVm().getId());
	}
	
	/**
	 * 
	 * Events to negotiate the migration of an application surrogate.
	 * 
	 */
	
	public void execute(SurrogateAppRequestEvent event) {
		
		simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - Surrogate request for App #%d.",
				manager.getCapability(RackManager.class).getRack().getId(),
				event.getAppId()));
		
		AppData app = manager.getCapability(AppPoolManager.class).getApplication(event.getAppId());
		
		if (null == app)
			throw new RuntimeException(String.format("[Rack #%d] Received a request for a surrogate of App #%d, but the surrogate cannot be found here.",
					manager.getCapability(RackManager.class).getRack().getId(),
					event.getAppId()));
		
		if (app.isMaster())
			throw new RuntimeException(String.format("[Rack #%d] Received a request for a surrogate of App #%d, but found that the master application is hosted here.",
					manager.getCapability(RackManager.class).getRack().getId(),
					app.getId()));
		
		// We expect there to be only one element in the list.
		for (TaskData task : app.getTasks()) {
			
			VmData vm = manager.getCapability(VmPoolManager.class).getVm(task.getHostingVm());
			
			// Mark VM as scheduled for migration.
			// TODO: As it is currently implemented, if the VM is migrating (or scheduled to do so), we stop the process -- notice that we are not sending any events.
			// This is not the right way of doing it, but it's the easy way right now (given the time constraints).
			// How it should actually work is: the VM shouldn't be marked as migrating at all here; if then the owner of the master application requests the VM
			// (actually, the surrogate app.) to be migrated back and the VM is migrating, the migration back should be queued until the current migration is completed
			// (or it is cancelled, if the migration hadn't actually started).
			MigrationTrackingManager ongoingMigs = manager.getCapability(MigrationTrackingManager.class);
			if (!ongoingMigs.isMigrating(vm.getId())) {
				ongoingMigs.addMigratingVm(vm.getId());
				
				// Send VM status data to the Rack manager that owns the application.
				simulation.sendEvent(new SurrogateAppDataEvent(app.getMaster(), app.getId(), vm.getCurrentStatus(), manager));
			}
		}
	}
	
	public void execute(SurrogateAppDataEvent event) {
		
		simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - Surrogate request for App #%d answered.",
				manager.getCapability(RackManager.class).getRack().getId(),
				event.getAppId()));
		
		VmPoolManager vmPool = manager.getCapability(VmPoolManager.class);
		
		VmStatus vm = event.getVm();
		TaskData task = vmPool.getVm(vm.getId()).getTask();
		
		// Create list of potential targets.
		ArrayList<HostData> targets = new ArrayList<HostData>();
		
		// AFFINITY: find the Host hosting the other tasks in the Affinity set.
		
		if (task.getConstraintType() == TaskConstraintType.AFFINITY) {
			
			// Find the Host of the tasks in the same Affinity set.
			// TODO: If we allow for more than 1 VM per app to be migrated away, this code won't work.
			// Instead, we need to loop over all tasks until finding one that it's hosted locally.
			// It could happen that all the tasks in an Affinity set were migrated away, in which case
			// the current task can be treated as INDEPENDENT.
			HostData targetHost = null;
			AppData app = manager.getCapability(AppPoolManager.class).getApplication(event.getAppId());
			for (Task t : app.getAffinitySet(task)) {
				if (t.getId() != task.getId()) {
					targetHost = vmPool.getVm(app.getTask(t.getId()).getHostingVm()).getHost();
					break;
				}
			}
			
			if (!this.isStressed(targetHost)) {
				targets.add(targetHost);
			}
			
		}
		else {		// Task constraint type = INDEPENDENT or ANTI_AFFINITY
			
			ArrayList<HostData> hosts = new ArrayList<HostData>(manager.getCapability(HostPoolManager.class).getHosts());
			
			// Reset the sandbox host status to the current host status.
			for (HostData host : hosts) {
				host.resetSandboxStatusToCurrent();
			}
			
			// ANTI-AFFINITY: purge every Host that is hosting an instance of the given task.
			
			if (task.getConstraintType() == TaskConstraintType.ANTI_AFFINITY) {
				
				// For each task instance, remove the Host currently hosting the VM carrying said instance.
				
				VmData taskVm = null;
				for (int vmId : task.getHostingVms()) {
					taskVm = vmPool.getVm(vmId);
					if (null != taskVm) {
						hosts.remove(taskVm.getHost());
					}
				}
			}
			
			// INDEPENDENT: Do nothing.
			
			// Classify Hosts as Partially-Utilized, Under-Utilized or Empty; ignore Stressed Hosts
			// and Hosts with currently invalid status.
			ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
			ArrayList<HostData> underUtilized = new ArrayList<HostData>();
			ArrayList<HostData> empty = new ArrayList<HostData>();
			this.classifyHosts(hosts, partiallyUtilized, underUtilized, empty);
			
			// Create target Hosts list.
			targets = this.orderTargetHosts(partiallyUtilized, underUtilized, empty);
		}
		
		HostData targetHost =  this.placeVmWherever(vm, targets);
		
		if (null != targetHost) {
			
			// Invalidate target Host' status, as we know it to be incorrect until the next status update arrives.
			targetHost.invalidateStatus(simulation.getSimulationTime());
			
			simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - Found target Host #%d for VM #%d, belonging to surrogate App #%d.",
					manager.getCapability(RackManager.class).getRack().getId(),
					targetHost.getId(),
					vm.getId(),
					event.getAppId()));
			
			// Request the Rack manager holding the surrogate app. to issue the migration.
			simulation.sendEvent(new SurrogateAppMigrateEvent(event.getOrigin(), event.getAppId(), vm.getId(), targetHost.getHost()));
		}
		else {
			simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - Could not find suitable target Host for surrogate App #%d.",
					manager.getCapability(RackManager.class).getRack().getId(),
					event.getAppId()));
			
			// Inform the Rack manager holding the surrogate app. that no target Host was found.
			simulation.sendEvent(new SurrogateAppRejectEvent(event.getOrigin(), event.getAppId(), vm.getId()));
		}
	}
	
	public void execute(SurrogateAppMigrateEvent event) {
		
		simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - Surrogate migration request for App #%d.",
				manager.getCapability(RackManager.class).getRack().getId(),
				event.getAppId()));
		
		VmData vmData = manager.getCapability(VmPoolManager.class).getVm(event.getVmId());
		HostData source = vmData.getHost();
		
		// Invalidate source Host' status, as we know it to be incorrect until the next status update arrives.
		source.invalidateStatus(simulation.getSimulationTime());
		
		// Send VmData information to target Rack. AppData (surrogate) will be ignored at destination (given that the original app. resides there).
		AppData surrogate = manager.getCapability(AppPoolManager.class).getApplication(event.getAppId());
		Collection<VmData> vms = new ArrayList<VmData>();
		vms.add(vmData);
		Map<Integer, Host> targetHostMap = new HashMap<Integer, Host>();
		targetHostMap.put(vmData.getId(), event.getTargetHost());
		simulation.sendEvent(new IncomingMigrationEvent(surrogate.getMaster(), surrogate, vms, targetHostMap, manager));
		
		simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - Migrating VM #%d from Host #%d to Host #%d.",
				manager.getCapability(RackManager.class).getRack().getId(),
				vmData.getId(),
				source.getId(),
				event.getTargetHost().getId()));
		
		// Trigger migration.
		new MigrationAction(source.getHostManager(), source.getHost(), event.getTargetHost(), event.getVmId()).execute(simulation, this);
	}
	
	public void execute(SurrogateAppRejectEvent event) {
		
		simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - Surrogate migration rejected for VM #%d, App #%d.",
				manager.getCapability(RackManager.class).getRack().getId(),
				event.getVmId(),
				event.getAppId()));
		
		// The VM had been marked for migration. Clear it.
		manager.getCapability(MigrationTrackingManager.class).removeMigratingVm(event.getVmId());
	}
	
	/**
	 * 
	 * Methods to support event handling.
	 * 
	 */
	
	/**
	 * Performs the Relocation process within the scope of the Rack. The process searches for
	 * a feasible VM migration with target Host inside the Rack.
	 */
	protected boolean performInternalVmRelocation(int hostId) {
		
		simulation.getLogger().debug(String.format("[Rack #%d] AppRelocationPolicyLevel1 - Internal Relocation process for Host #%d.",
				manager.getCapability(RackManager.class).getRack().getId(),
				hostId));
		
		MigrationTrackingManager ongoingMigs = manager.getCapability(MigrationTrackingManager.class);
		VmPoolManager vmPool = manager.getCapability(VmPoolManager.class);
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
					
					// Mark VM as scheduled for migration.
					ongoingMigs.addMigratingVm(vm.getId());
					
					mig = new MigrationAction(source.getHostManager(), source.getHost(), target.getHost(), vm.getId());
					
					simulation.getLogger().debug(String.format("[Rack #%d] AppRelocationPolicyLevel1 - Migrating (Independent) VM #%d from Host #%d to Host #%d.",
							manager.getCapability(RackManager.class).getRack().getId(),
							vm.getId(),
							source.getId(),
							target.getId()));
					
					break;			// Found VM migration. Exit loop.
				}
			}
			
			if (null != mig)		// Found VM migration. Exit loop.
				break;
		}
		
		if (null != mig) {			// Trigger migration and exit.
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
					!this.isHostingTask(vmPool.getVm(vm.getId()).getTask(), target)) {
					
					// Modify host and VM states to record the future migration. Note that we 
					// can do this because we are using the designated 'sandbox' host status.
					source.getSandboxStatus().migrate(vm, target.getSandboxStatus());
					
					// Invalidate source and target status, as we know them to be incorrect until the next status update arrives.
					source.invalidateStatus(simulation.getSimulationTime());
					target.invalidateStatus(simulation.getSimulationTime());
					
					// Mark VM as scheduled for migration.
					ongoingMigs.addMigratingVm(vm.getId());
					
					mig = new MigrationAction(source.getHostManager(), source.getHost(), target.getHost(), vm.getId());
					
					simulation.getLogger().debug(String.format("[Rack #%d] AppRelocationPolicyLevel1 - Migrating (Anti-affinity) VM #%d from Host #%d to Host #%d.",
							manager.getCapability(RackManager.class).getRack().getId(),
							vm.getId(),
							source.getId(),
							target.getId()));
					
					break;			// Found VM migration. Exit loop.
				}
			}
			
			if (null != mig)		// Found VM migration. Exit loop.
				break;
		}
		
		if (null != mig) {			// Trigger migration and exit.
			mig.execute(simulation, this);
			return true;
		}
		
		// Process Affinity VMs.
		
		// TODO: Migrations are issued concurrently. Should they be issued sequentially instead?
		ConcurrentManagementActionExecutor migs = null;
		
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
					
					migs = new ConcurrentManagementActionExecutor();
					
					// Modify host and VM states to record the future migration. Note that we 
					// can do this because we are using the designated 'sandbox' host status.
					// Create a migration action per VM in the Affinity-set.
					for (VmStatus vm : affinitySet) {
						source.getSandboxStatus().migrate(vm, target.getSandboxStatus());
						
						// Mark VM as scheduled for migration.
						ongoingMigs.addMigratingVm(vm.getId());
						
						migs.addAction(new MigrationAction(source.getHostManager(), source.getHost(), target.getHost(), vm.getId()));
						
						simulation.getLogger().debug(String.format("[Rack #%d] AppRelocationPolicyLevel1 - Migrating (Affinity) VM #%d from Host #%d to Host #%d.",
								manager.getCapability(RackManager.class).getRack().getId(),
								vm.getId(),
								source.getId(),
								target.getId()));
						
					}
					
					// Invalidate source and target status, as we know them to be incorrect until the next status update arrives.
					source.invalidateStatus(simulation.getSimulationTime());
					target.invalidateStatus(simulation.getSimulationTime());
					
					break;			// Found VM migration. Exit loop.
				}
			}
			
			if (null != migs)		// Found VM migration. Exit loop.
				break;
		}
		
		if (null != migs) {			// Trigger migrations and exit.
			migs.execute(simulation, this);
			return true;
		}
		
		return false;
	}
	
	/**
	 * Starts an external VM Relocation process.
	 * 
	 * A VM from the stressed Host is selected to be migrated away from the Rack, giving preference to
	 * VMs hosting single-VM applications, then VMs hosting Independent tasks, and finally all the rest.
	 * 
	 * The ClusterManager is contacted for it to find a target Rack to which to migrate the selected VM.
	 */
	protected boolean performExternalVmRelocation(int hostId) {
		
		simulation.getLogger().debug(String.format("[Rack #%d] AppRelocationPolicyLevel1 - External Relocation process for Host #%d.",
				manager.getCapability(RackManager.class).getRack().getId(),
				hostId));
		
		MigrationTrackingManager ongoingMigs = manager.getCapability(MigrationTrackingManager.class);
		HostData host = manager.getCapability(HostPoolManager.class).getHost(hostId);
		ArrayList<VmStatus> vms = this.orderSourceVms(host.getCurrentStatus().getVms(), host);
		
		// Search for a single-VM application.
		// Also, find the first VM hosting an Independent or Anti-affinity task -- non-affinity.
		// And the first VM hosting an Affinity task.
		VmStatus candidateVm = null;
		VmStatus nonAffinityVm = null;
		VmStatus affinityVm = null;
		VmPoolManager vmPool = manager.getCapability(VmPoolManager.class);
		AppPoolManager appPool = manager.getCapability(AppPoolManager.class);
		for (VmStatus vm : vms) {
			
			// Skip VMs migrating out or scheduled to do so.
			if (ongoingMigs.isMigrating(vm.getId()))
				continue;
			
			TaskData task = vmPool.getVm(vm.getId()).getTask();
			
			if (appPool.getApplication(task.getAppId()).size() == 1) {
				candidateVm = vm;
				break;
			}
			
			if (null == nonAffinityVm && task.getConstraintType() != TaskConstraintType.AFFINITY)
				nonAffinityVm = vm;
			
			if (null == affinityVm && task.getConstraintType() == TaskConstraintType.AFFINITY)
				affinityVm = vm;
		}
		
		if (null != candidateVm) {				// Found single-VM application.
			
			AppStatus application = this.generateAppStatus(candidateVm);
			
			simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - Trying to migrate away App #%d (#vms = %d).",
					manager.getCapability(RackManager.class).getRack().getId(),
					application.getId(),
					application.getAllVms().size()));
			
			// Mark VMs as scheduled for migration.
			for (VmStatus vm : application.getAllVms()) {
				ongoingMigs.addMigratingVm(vm.getId());
			}
			
			// Request assistance from ClusterManager to find a target Rack to which to migrate the selected application.
			simulation.sendEvent(new AppMigRequestEvent(target, application, manager, manager.getCapability(RackManager.class).getRack().getId()));
			
			// Keep track of the migration request just sent.
			manager.getCapability(MigRequestRecord.class).addEntry(new MigRequestEntry(application, manager));
			
			return true;
		}
		else if (null != nonAffinityVm)		// Found VM hosting an Independent or Anti-affinity task.
			candidateVm = nonAffinityVm;
		else if (null != affinityVm)			// Found VM hosting an Affinity task.
			candidateVm = affinityVm;
		else
			return false;						// Could not find a VM to migrate.
		
		simulation.getLogger().debug(String.format("[Rack #%d] RelocationPolicyLevel1 - Trying to migrate away VM #%d.",
				manager.getCapability(RackManager.class).getRack().getId(),
				candidateVm.getId()));
		
		// Mark VM as scheduled for migration.
		ongoingMigs.addMigratingVm(candidateVm.getId());
		
		// Request assistance from ClusterManager to find a target Rack to which to migrate the selected VM.
		simulation.sendEvent(new VmMigRequestEvent(target, candidateVm, manager, manager.getCapability(RackManager.class).getRack().getId()));
		
		// Keep track of the migration request just sent.
		manager.getCapability(MigRequestRecord.class).addEntry(new MigRequestEntry(candidateVm, manager));
		
		return true;
	}
	
	/**
	 * Search for a target Host that could take the given VM.
	 */
	protected HostData findMigrationTarget(VmStatus vm) {
		Collection<HostData> hosts = manager.getCapability(HostPoolManager.class).getHosts();
		
		// Reset the sandbox host status to the current host status.
		for (HostData host : hosts) {
			host.resetSandboxStatusToCurrent();
		}
		
		// Classify Hosts as Partially-Utilized, Under-Utilized or Empty; ignore Stressed Hosts
		// and Hosts with currently invalid status.
		ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
		ArrayList<HostData> underUtilized = new ArrayList<HostData>();
		ArrayList<HostData> empty = new ArrayList<HostData>();
		this.classifyHosts(hosts, partiallyUtilized, underUtilized, empty);
		
		// Create target Hosts list.
		ArrayList<HostData> targets = this.orderTargetHosts(partiallyUtilized, underUtilized, empty);
		
		return this.placeVmWherever(vm, targets);
	}
	
	protected Map<Integer, HostData> findMigrationTargets(AppStatus application) {
		Map<Integer, HostData> vmHostMap = new HashMap<Integer, HostData>();
		
		Collection<HostData> hosts = manager.getCapability(HostPoolManager.class).getHosts();
		
		// Reset the sandbox host status to the current host status.
		for (HostData host : hosts) {
			host.resetSandboxStatusToCurrent();
		}
		
		// Classify Hosts as Partially-Utilized, Under-Utilized or Empty; ignore Stressed Hosts
		// and Hosts with currently invalid status.
		ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
		ArrayList<HostData> underUtilized = new ArrayList<HostData>();
		ArrayList<HostData> empty = new ArrayList<HostData>();
		this.classifyHosts(hosts, partiallyUtilized, underUtilized, empty);
		
		// Create target Hosts list.
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
		VmPoolManager vmPool = manager.getCapability(VmPoolManager.class);
		
		for (VmStatus vm : vms) {
			
			switch (vmPool.getVm(vm.getId()).getTask().getConstraintType()) {
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
		VmPoolManager vmPool = manager.getCapability(VmPoolManager.class);
		
		for (VmStatus vm : vms) {
			TaskData vmTask = vmPool.getVm(vm.getId()).getTask();
			if (vmTask.getId() == task.getId() && vmTask.getAppId() == task.getApplication().getId())
				return vm;
		}
		
		return null;
	}
	
	private AppStatus generateAppStatus(VmStatus vm) {
		VmPoolManager vmPool = manager.getCapability(VmPoolManager.class);
		AppData application = manager.getCapability(AppPoolManager.class).getApplication(vmPool.getVm(vm.getId()).getTask().getAppId());
		
		Map<Integer, VmStatus> vmStatusMap = new HashMap<Integer, VmStatus>();
		for (VmData vmData : vmPool.getVms(application.getHostingVmsIds())) {
			vmStatusMap.put(vmData.getId(), vmData.getCurrentStatus());
		}
		
		return new AppStatus(application, vmStatusMap);
	}
	
	private ArrayList<ArrayList<VmStatus>> groupVmsByAffinity(ArrayList<VmStatus> vms) {
		ArrayList<ArrayList<VmStatus>> affinitySets = new ArrayList<ArrayList<VmStatus>>();
		
		ArrayList<VmStatus> copy = new ArrayList<VmStatus>(vms);
		while (copy.size() > 0) {
			VmStatus vm = copy.get(0);
			
			// Get Affinity-set for the Task instance hosted in this VM.
			TaskData vmTask = manager.getCapability(VmPoolManager.class).getVm(vm.getId()).getTask();
			ArrayList<InteractiveTask> affinitySet = manager.getCapability(AppPoolManager.class).getApplication(vmTask.getAppId()).getAffinitySet(vmTask);
			
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
	private boolean isHostingTask(TaskData task, HostData host) {
		VmPoolManager vmPool = manager.getCapability(VmPoolManager.class);
		
		for (VmStatus vm : host.getSandboxStatus().getVms()) {
			TaskData vmTask = vmPool.getVm(vm.getId()).getTask();
			if (vmTask.getId() == task.getId() && vmTask.getAppId() == task.getAppId())
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
