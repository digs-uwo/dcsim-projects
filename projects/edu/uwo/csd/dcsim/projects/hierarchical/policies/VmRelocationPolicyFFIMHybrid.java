package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.management.*;

/**
 * Implements the following VM Relocation policy:
 * 
 * - relocation candidates: VMs with less CPU load than the CPU load by which 
 *   the host is stressed are ignored. The rest of the VMs are sorted in 
 *   increasing order by <CPU load>;
 * - target hosts: sort Partially-Utilized hosts in increasing order by 
 *   <CPU utilization, power efficiency>, Underutilized hosts in decreasing 
 *   order by <CPU utilization, power efficiency>, and Empty hosts in 
 *   decreasing order by <power efficiency, power state>. Return the hosts in 
 *   the following order: Partially-Utilized, Underutilized, and Empty.
 * 
 * @author Gaston Keller
 *
 */
public class VmRelocationPolicyFFIMHybrid extends VmRelocationPolicyLevel0 {

	/**
	 * Creates a new instance of VmRelocationPolicyFFIMHybrid.
	 */
	public VmRelocationPolicyFFIMHybrid(AutonomicManager target, double lowerThreshold, double upperThreshold, double targetUtilization) {
		super(target, lowerThreshold, upperThreshold, targetUtilization);
	}

	/**
	 * Sorts the relocation candidates in increasing order by <CPU load>, 
	 * previously removing from consideration those VMs with less CPU load 
	 * than the CPU load by which the host is stressed.
	 */
	@Override
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
	 * Sorts Partially-Utilized hosts in increasing order by <CPU utilization, 
	 * power efficiency>, Underutilized hosts in decreasing order by 
	 * <CPU utilization, power efficiency>, and Empty hosts in decreasing 
	 * order by <power efficiency, power state>.
	 * 
	 * Returns Partially-utilized, Underutilized, and Empty hosts, in that 
	 * order.
	 */
	@Override
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

}
