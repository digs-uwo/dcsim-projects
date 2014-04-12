package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.action.MigrationAction;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.projects.centralized.events.StressCheckEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.MigRequestEntry;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.*;
import edu.uwo.csd.dcsim.projects.hierarchical.events.*;

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
	public void execute(MigRejectEvent event) {
		// Delete entry from migration requests record.
		MigRequestRecord record = manager.getCapability(MigRequestRecord.class);
		record.removeEntry(record.getEntry(event.getVm(), event.getOrigin()));
	}
	
	/**
	 * This event comes from the Cluster Manager, trying to migrate the VM to this Rack.
	 */
	public void execute(MigRequestEvent event) {
		// Search for potential target Host.
		HostData targetHost = this.findTargetHost(event.getVm());
		
		// If found, send message to RackManager origin accepting the migration request.
		if (null != targetHost) {
			// Invalidate target Host' status, as we know it to be incorrect until the next status update arrives.
			targetHost.invalidateStatus(simulation.getSimulationTime());
			
			simulation.sendEvent(new MigAcceptEvent(event.getOrigin(), event.getVm(), targetHost.getHost()));
			
		}
		// Otherwise, send message to ClusterManager rejecting the migration request.
		else {
			int rackId = manager.getCapability(RackManager.class).getRack().getId();
			simulation.sendEvent(new MigRejectEvent(target, event.getVm(), event.getOrigin(), rackId));
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
	protected HostData findTargetHost(VmStatus vm) {
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		Collection<HostData> hosts = hostPool.getHosts();
		
		// Reset the sandbox host status to the current host status.
		for (HostData host : hosts) {
			host.resetSandboxStatusToCurrent();
		}
		
		// Classify Hosts as Partially-Utilized, Under-Utilized or Empty; ignore Stressed Hosts.
		ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
		ArrayList<HostData> underUtilized = new ArrayList<HostData>();
		ArrayList<HostData> empty = new ArrayList<HostData>();
		this.classifyHosts(hosts, partiallyUtilized, underUtilized, empty);
		
		// Create sorted list of target Hosts.
		ArrayList<HostData> targets = this.orderTargetHosts(partiallyUtilized, underUtilized, empty);
		
		for (HostData target : targets) {
			// Check that target host has at most 1 incoming migration pending, 
			// that target host is capable and has enough capacity left to host the VM, 
			// and also that it will not exceed the target utilization.
			if (target.getSandboxStatus().getIncomingMigrationCount() < 2 && 
				HostData.canHost(vm, target.getSandboxStatus(), target.getHostDescription()) && 
				(target.getSandboxStatus().getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
				
				return target;
			}
		}
		
		return null;
	}
	
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
		
		// Classify Hosts as Partially-Utilized, Under-Utilized or Empty; ignore Stressed Hosts.
		ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
		ArrayList<HostData> underUtilized = new ArrayList<HostData>();
		ArrayList<HostData> empty = new ArrayList<HostData>();
		this.classifyHosts(hosts, partiallyUtilized, underUtilized, empty);
		
		// Create sorted list of target Hosts.
		ArrayList<HostData> targets = this.orderTargetHosts(partiallyUtilized, underUtilized, empty);
		
		MigrationAction mig = null;
		ArrayList<VmStatus> vmList = this.orderSourceVms(source.getCurrentStatus().getVms(), source);
		for (VmStatus vm : vmList) {
			
			for (HostData target : targets) {
				// Check that target host has at most 1 incoming migration pending, 
				// that target host is capable and has enough capacity left to host the VM, 
				// and also that it will not exceed the target utilization.
				if (target.getSandboxStatus().getIncomingMigrationCount() < 2 && 
					HostData.canHost(vm, target.getSandboxStatus(), target.getHostDescription()) && 
					(target.getSandboxStatus().getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
					
					// Modify host and vm states to record the future migration. Note that we 
					// can do this because we are using the designated 'sandbox' host status.
					source.getSandboxStatus().migrate(vm, target.getSandboxStatus());
					
					// Invalidate source and target status, as we know them to be incorrect until the next status update arrives.
					source.invalidateStatus(simulation.getSimulationTime());
					target.invalidateStatus(simulation.getSimulationTime());
					
					mig = new MigrationAction(source.getHostManager(), source.getHost(), target.getHost(), vm.getId());
					
					break;		// Found VM migration. Exit loop.
				}
			}
			
			if (mig != null)	// Found VM migration. Exit loop.
				break;
		}
		
		if (mig != null) {		// Trigger migration.
			mig.execute(simulation, this);
			return true;
		}
		
		return false;
	}
	
	/**
	 * Starts an external VM Relocation process.
	 * 
	 * A VM from the stressed Host is selected to be migrated away from the Rack.
	 * The ClusterManager is contacted for it to find a target Host for the VM migration.
	 */
	protected void performExternalVmRelocation(int hostId) {
		HostData host = manager.getCapability(HostPoolManager.class).getHost(hostId);
		int rackId = manager.getCapability(RackManager.class).getRack().getId();
		
		// Get VM to migrate away -- the first one in the ordered list.
		VmStatus vm = this.orderSourceVms(host.getCurrentStatus().getVms(), host).get(0);
		
		// Request assistance from ClusterManager to find a target Host for migrating the selected VM.
		simulation.sendEvent(new MigRequestEvent(target, vm, manager, rackId));
		
		// Keep track of the migration request just sent.
		manager.getCapability(MigRequestRecord.class).addEntry(new MigRequestEntry(vm, manager, host));
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
