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
 * - target hosts: sort Partially-Utilized hosts in increasing order by 
 *   <CPU utilization, power efficiency>, Underutilized hosts in decreasing 
 *   order by <CPU utilization, power efficiency>, and Empty hosts in 
 *   decreasing order by <power efficiency, power state>. Return the hosts in 
 *   the following order: Partially-Utilized, Underutilized, and Empty;
 * - source hosts: any host with its *two* last load monitoring measures 
 *   exceeding _upperThreshold_ or its average CPU utilization over the last 
 *   window of time exceeding _upperThreshold_ is considered Stressed. These 
 *   hosts are sorted in decreasing order by CPU utilization.
 * 
 * @author Gaston Keller
 *
 */
public class VmRelocationPolicyFFIMDSla extends VmRelocationPolicyGreedy {

	/**
	 * Creates a new instance of VmRelocationPolicyFFIMDSla.
	 */
	public VmRelocationPolicyFFIMDSla(double lowerThreshold, double upperThreshold, double targetUtilization) {
		super(lowerThreshold, upperThreshold, targetUtilization);
	}

	/**
	 * Sorts the relocation candidates in increasing order by CPU load, 
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
	 * Sorts Partially-Utilized hosts in increasing order by <CPU utilization, 
	 * power efficiency>, Underutilized hosts in decreasing order by 
	 * <CPU utilization, power efficiency>, and Empty hosts in decreasing 
	 * order by <power efficiency, power state>.
	 * 
	 * Returns Partially-Utilized, Underutilized, and Empty hosts, in that 
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
	
	/**
	 * Classifies hosts as Stressed, Partially-Utilized, Underutilized or 
	 * Empty based on the hosts' average CPU utilization over the last window 
	 * of time.
	 * 
	 * If a host's last *two* load monitoring measures exceed 
	 * _upperThreshold_ , the host is considered Stressed.
	 */
	@Override
	protected void classifyHosts(ArrayList<HostData> stressed, 
			ArrayList<HostData> partiallyUtilized, 
			ArrayList<HostData> underUtilized, 
			ArrayList<HostData> empty,
			Collection<HostData> hosts) {
		
		int n = 2;	// number of load monitoring measures to check to determine stress
		
		for (HostData host : hosts) {
			
			// Filter out hosts with a currently invalid status.
			if (host.isStatusValid()) {
				
				if (host.getCurrentStatus().getVms().size() == 0)
					empty.add(host);
				else {
					// Check if the last *two* load measures exceed _upperThreshold_.
					int counter = 0;
					boolean stress = true;
					for (HostStatus status : host.getHistory()) {
						double cpuUtil = Utility.roundDouble(status.getResourcesInUse().getCpu() / host.getHostDescription().getResourceCapacity().getCpu());
						stress = stress && cpuUtil > upperThreshold;
						if (++counter == n)
							break;
					}
					if (stress)
						stressed.add(host);
					else {
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
						
						// Classify hosts.
						if (avgCpuUtilization < lowerThreshold)
							underUtilized.add(host);
						else if (avgCpuUtilization > upperThreshold)
							stressed.add(host);
						else
							partiallyUtilized.add(host);
					}
				}
			}
		}
	}

}
