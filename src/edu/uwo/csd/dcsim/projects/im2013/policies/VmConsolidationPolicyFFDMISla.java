package edu.uwo.csd.dcsim.projects.im2013.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.HostDataComparator;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.VmStatusComparator;

/**
 * Implements the following VM Consolidation policy:
 * 
 * - relocation candidates: sort VMs in decreasing order by <overall capacity, 
 *   CPU load>;
 * - target hosts: sort Partially-utilized hosts in increasing order by 
 *   <CPU utilization, power efficiency> and Underutilized hosts in decreasing 
 *   order by <CPU utilization, power efficiency>. Return Partially-utilized 
 *   hosts, followed by Underutilized hosts;
 * - source hosts: sort Underutilized hosts in increasing order by 
 *   <CPU utilization>.
 * 
 * @author Gaston Keller
 *
 */
public class VmConsolidationPolicyFFDMISla extends VmConsolidationPolicyGreedy {

	/**
	 * Creates a new instance of VmConsolidationPolicyFFDMISla.
	 */
	public VmConsolidationPolicyFFDMISla(double lowerThreshold, double upperThreshold, double targetUtilization) {
		super(lowerThreshold, upperThreshold, targetUtilization);
	}

	/**
	 * Sorts VMs in decreasing order by <overall capacity, CPU load>, so as to 
	 * place the _biggest_ VMs first.
	 * 
	 * (Note: since CPU can be oversubscribed, but memory can't, memory takes 
	 * priority over CPU when comparing VMs by _size_ (capacity).)
	 */
	@Override
	protected ArrayList<VmStatus> orderSourceVms(ArrayList<VmStatus> sourceVms) {
		ArrayList<VmStatus> sources = new ArrayList<VmStatus>(sourceVms);
		
		// Sort VMs in decreasing order by <overall capacity, CPU load>.
		// (Note: since CPU can be oversubscribed, but memory can't, memory 
		// takes priority over CPU when comparing VMs by _size_ (capacity).)
		Collections.sort(sources, VmStatusComparator.getComparator(
				VmStatusComparator.MEMORY, 
				VmStatusComparator.CPU_CORES, 
				VmStatusComparator.CORE_CAP, 
				VmStatusComparator.CPU_IN_USE));
		Collections.reverse(sources);
		
		return sources;
	}

	/**
	 * Sorts Underutilized hosts in increasing order by <CPU utilization>.
	 */
	@Override
	protected ArrayList<HostData> orderSourceHosts(ArrayList<HostData> underUtilized) {
		ArrayList<HostData> sources = new ArrayList<HostData>(underUtilized);
		
		// Sort Underutilized hosts in increasing order by <CPU utilization>.
		Collections.sort(sources, HostDataComparator.getComparator(HostDataComparator.CPU_UTIL));
		
		return sources;
	}

	/**
	 * Sorts Partially-utilized hosts in increasing order by <CPU utilization, 
	 * power efficiency> and Underutilized hosts in decreasing order by 
	 * <CPU utilization, power efficiency>.
	 * 
	 * Returns Partially-utilized hosts, followed by Underutilized hosts.
	 */
	@Override
	protected ArrayList<HostData> orderTargetHosts(ArrayList<HostData> partiallyUtilized, ArrayList<HostData> underUtilized) {
		ArrayList<HostData> targets = new ArrayList<HostData>();
		
		// Sort Partially-utilized in increasing order by <CPU utilization, power efficiency>.
		Collections.sort(partiallyUtilized, HostDataComparator.getComparator(HostDataComparator.CPU_UTIL, HostDataComparator.EFFICIENCY));
		
		// Sort Underutilized hosts in decreasing order by CPU utilization, power efficiency>.
		Collections.sort(underUtilized, HostDataComparator.getComparator(HostDataComparator.CPU_UTIL, HostDataComparator.EFFICIENCY));
		Collections.reverse(underUtilized);
		
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		
		return targets;
	}

}
