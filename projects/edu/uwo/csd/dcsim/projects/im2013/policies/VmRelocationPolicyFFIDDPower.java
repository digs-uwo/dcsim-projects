package edu.uwo.csd.dcsim.projects.im2013.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.HostDataComparator;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.VmStatusComparator;

/**
 * Implements the following VM Relocation policy:
 * 
 * - relocation candidates: VMs with less CPU load than the CPU load by which 
 *   the host is stressed are ignored. The rest of the VMs are sorted in 
 *   increasing order by CPU load;
 * - target hosts: sort Partially-Utilized and Underutilized hosts in 
 *   decreasing order by <power efficiency, CPU utilization>, and Empty hosts 
 *   in decreasing order by <power efficiency, power state>. Return the hosts 
 *   in the following order: Partially-Utilized and Underutilized hosts first, 
 *   followed by Empty hosts;
 * - source hosts: only those hosts whose CPU utilization has remained above 
 *   the _upperThreshold_ *all the time* over the last CPU load monitoring 
 *   window are considered Stressed. These hosts are sorted in decreasing 
 *   order by CPU utilization.
 * 
 * @author Gaston Keller
 *
 */
public class VmRelocationPolicyFFIDDPower extends VmRelocationPolicyGreedy {

	/**
	 * Creates a new instance of VmRelocationPolicyFFIDDPower.
	 */
	public VmRelocationPolicyFFIDDPower(double lowerThreshold, double upperThreshold, double targetUtilization) {
		super(lowerThreshold, upperThreshold, targetUtilization);
	}

	/**
	 * Sorts the relocation candidates in increasing order by CPU load, 
	 * previously removing from consideration those VMs with less CPU load 
	 * than the CPU load by which the host is stressed.
	 */
	@Override
	protected ArrayList<VmStatus> orderSourceVms(ArrayList<VmStatus> sourceVms) {
		ArrayList<VmStatus> sorted = new ArrayList<VmStatus>();
		
		// Remove VMs with less CPU load than the CPU load by which the source 
		// host is stressed.
		HostData source = sourceVms.get(0).getHost();
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
	 * Sorts Stressed hosts in decreasing order by CPU utilization.
	 */
	@Override
	protected ArrayList<HostData> orderSourceHosts(ArrayList<HostData> stressed) {
		ArrayList<HostData> sorted = new ArrayList<HostData>(stressed);
		
		// Sort Stressed hosts in decreasing order by CPU utilization.
		Collections.sort(sorted, HostDataComparator.getComparator(HostDataComparator.CPU_UTIL));
		Collections.reverse(sorted);
		
		return sorted;
	}

	/**
	 * Sorts Partially-Utilized and Underutilized hosts in decreasing order by 
	 * <power efficiency, CPU utilization>, and Empty hosts in decreasing 
	 * order by <power efficiency, power state>.
	 * 
	 * Returns Partially-Utilized and Underutilized hosts first, followed by 
	 * Empty hosts.
	 */
	@Override
	protected ArrayList<HostData> orderTargetHosts(ArrayList<HostData> partiallyUtilized, ArrayList<HostData> underUtilized, ArrayList<HostData> empty) {
		ArrayList<HostData> targets = new ArrayList<HostData>();
		
		// Sort Partially-utilized and Underutilized hosts in decreasing order by <power efficiency, CPU utilization>.
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		Collections.sort(targets, HostDataComparator.getComparator(HostDataComparator.EFFICIENCY, HostDataComparator.CPU_UTIL));
		Collections.reverse(targets);
		
		// Sort Empty hosts in decreasing order by <power efficiency, power state>.
		Collections.sort(empty, HostDataComparator.getComparator(HostDataComparator.EFFICIENCY, HostDataComparator.PWR_STATE));
		Collections.reverse(empty);
		
		targets.addAll(empty);
		
		return targets;
	}
	
	/**
	 * Classifies hosts as Stressed, Partially-Utilized, Underutilized or 
	 * Empty based on the hosts' average CPU utilization over the last window 
	 * of time.
	 * 
	 * Hosts in the Stressed category are parsed once more and demoted to the 
	 * Partially-Utilized category if any of their load measures in the last 
	 * window was *not* above the _upperThreshold_.
	 */
	@Override
	protected void classifyHosts(ArrayList<HostData> stressed, 
			ArrayList<HostData> partiallyUtilized, 
			ArrayList<HostData> underUtilized, 
			ArrayList<HostData> empty,
			Collection<HostData> hosts) {
		
		// Categorize hosts according to avg. CPU utilization over the last 
		// window of time (parent class method).
		super.classifyHosts(stressed, partiallyUtilized, underUtilized, empty, hosts);
		
		// Check hosts in the Stressed list so that only those hosts that were 
		// stressed *all the time* over the last window remain in the Stressed 
		// category.
		for (HostData host : stressed) {
			boolean stressedAllTime = true;
			for (HostStatus status : host.getHistory()) {
				stressedAllTime = stressedAllTime && status.getResourcesInUse().getCpu() > upperThreshold;
			}
			if (!stressedAllTime)
				stressed.remove(host);
		}
	}

}
