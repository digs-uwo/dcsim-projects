package edu.uwo.csd.dcsim.projects.overloadProbability.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.HostDataComparator;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.VmStatusComparator;
import edu.uwo.csd.dcsim.management.action.MigrationAction;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.projects.overloadProbability.capabilities.VmMarkovChainManager;

/**
 * Implements a greedy algorithm for VM Relocation. VMs are migrated out of 
 * Stressed hosts and into Partially-Utilized, Underutilized or Empty hosts.
 * 
 * Hosts are classified as Stressed, Partially-Utilized, Underutilized or 
 * Empty based on the hosts' average CPU utilization over the last window of 
 * time.
 * 
 * @author Gaston Keller
 *
 */
public class VmRelocationPolicyProbability extends Policy {

	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	protected double pThreshold;
	
	/**
	 * Creates an instance of VmRelocationPolicyGreedy.
	 */
	public VmRelocationPolicyProbability(double lowerThreshold, double upperThreshold, double targetUtilization, double pThreshold) {
		addRequiredCapability(HostPoolManager.class);
		addRequiredCapability(VmMarkovChainManager.class);
		
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
		this.pThreshold = pThreshold;
	}
	
	/**
	 * Sorts the relocation candidates in increasing order by <CPU load>, 
	 * previously removing from consideration those VMs with less CPU load 
	 * than the CPU load by which the host is stressed.
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
	 * Sorts Stressed hosts in decreasing order by <CPU utilization>.
	 */
	protected ArrayList<HostData> orderSourceHosts(ArrayList<HostData> stressed) {
		ArrayList<HostData> sorted = new ArrayList<HostData>(stressed);
		
		// Sort Stressed hosts in decreasing order by CPU utilization.
		Collections.sort(sorted, HostDataComparator.getComparator(HostDataComparator.CPU_UTIL));
		Collections.reverse(sorted);
		
		return sorted;
	}
	
	/**
	 * Sorts Partially-Utilized hosts in increasing order by <CPU utilization, 
	 * power efficiency>, Underutilized hosts in decreasing order by 
	 * <CPU utilization, power efficiency>, and Empty hosts in decreasing 
	 * order by <power efficiency, power state>.
	 * 
	 * Returns Partially-utilized, Underutilized, and Empty hosts, in that 
	 * order.
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
	
	/**
	 * Performs the VM Relocation process.
	 */
	public void execute() {
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		Collection<HostData> hosts = hostPool.getHosts();
		
		// Reset the sandbox host status to the current host status.
		for (HostData host : hosts) {
			host.resetSandboxStatusToCurrent();
		}
		
		// Categorize hosts.
		ArrayList<HostData> stressed = new ArrayList<HostData>();
		ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
		ArrayList<HostData> underUtilized = new ArrayList<HostData>();
		ArrayList<HostData> empty = new ArrayList<HostData>();
		
		this.classifyHosts(stressed, partiallyUtilized, underUtilized, empty, hosts);
		
		// Create (sorted) source and target lists.
		ArrayList<HostData> sources = this.orderSourceHosts(stressed);
		ArrayList<HostData> targets = this.orderTargetHosts(partiallyUtilized, underUtilized, empty);
		ArrayList<MigrationAction> migrations = new ArrayList<MigrationAction>();
		
		for (HostData source : sources) {
			
			boolean found = false;
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
						
						migrations.add(new MigrationAction(source.getHostManager(),
								source.getHost(),
								target.getHost(), 
								vm.getId()));
						
						found = true;
						break;
					}
				}
				
				if (found)
					break;
			}
		}
		
		// Trigger migrations.
		for (MigrationAction migration : migrations) {
			migration.execute(simulation, this);
		}
	}
	
	/**
	 * Classifies hosts as Stressed, Partially-Utilized, Underutilized or 
	 * Empty based on the hosts' average CPU utilization over the last window 
	 * of time.
	 */
	protected void classifyHosts(ArrayList<HostData> stressed, 
			ArrayList<HostData> partiallyUtilized, 
			ArrayList<HostData> underUtilized, 
			ArrayList<HostData> empty,
			Collection<HostData> hosts) {
		
		VmMarkovChainManager mcMan = manager.getCapability(VmMarkovChainManager.class);
		
		for (HostData host : hosts) {
			
			// Filter out hosts with a currently invalid status.
			if (host.isStatusValid()) {
					
				// Calculate host's avg CPU utilization over the last window of time.
				double avgCpuInUse = 0;
				int count = 0;
				for (HostStatus status : host.getHistory()) {
					// Only consider times when the host is powered on.
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
				
				double avgCpuUtilization = Utility.roundDouble(avgCpuInUse / host.getHostDescription().getResourceCapacity().getCpu());
								
				if (host.getCurrentStatus().getVms().size() == 0) {
					empty.add(host);
				} else if (avgCpuUtilization < lowerThreshold) {
					underUtilized.add(host);
				} else if (mcMan.calculateOverloadProbability(host) >= pThreshold) {
					System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! Stress Probability High!");
					stressed.add(host);
				} else {
					partiallyUtilized.add(host);
				}
			}
		}
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
