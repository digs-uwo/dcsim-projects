package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.DCUtilizationMonitor;
import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.management.stub.*;

/**
 * Implements the following VM consolidation policy:
 * 
 * - relocation candidates: sort VMs in decreasing order by overall capacity 
 *   and CPU load, i.e. <memory, cpu cores, core capacity, CPU load>;
 * - target hosts: sort Partially-utilized hosts in increasing order by 
 *   <CPU utilization, power efficiency>, and Underutilized hosts in 
 *   decreasing order by <CPU utilization, power efficiency>. Return 
 *   Partially-utilized hosts, followed by Underutilized hosts;
 * - source hosts: sort Underutilized hosts in increasing order by 
 *   <power efficiency, CPU load>.
 * 
 * @author Gaston Keller
 *
 */
public class VMConsolidationPolicyFFDMIBalanced extends VMConsolidationPolicyGreedy {

	/**
	 * Creates a new instance of VMConsolidationPolicyFFDMIBalanced.
	 */
	public VMConsolidationPolicyFFDMIBalanced(DataCentre dc, DCUtilizationMonitor utilizationMonitor, double lowerThreshold, double upperThreshold, double targetUtilization) {
		super(dc, utilizationMonitor, lowerThreshold, upperThreshold, targetUtilization);
	}
	
	/**
	 * Sorts VMs in decreasing order by overall capacity and CPU load, i.e. 
	 * <memory, cpu cores, core capacity, CPU load>, so as to place the 
	 * _biggest_ VMs first.
	 * 
	 * (Note: since CPU can be oversubscribed, but memory can't, memory takes 
	 * priority over CPU when comparing VMs by _size_ (capacity).)
	 */
	@Override
	protected ArrayList<VmStub> orderSourceVms(ArrayList<VmStub> sourceVms) {
		ArrayList<VmStub> sources = new ArrayList<VmStub>(sourceVms);
		
		// Sort VMs in decreasing order by <memory, cpu cores, core capacity, 
		// CPU load>.
		// (Note: since CPU can be oversubscribed, but memory can't, memory 
		// takes priority over CPU when comparing VMs by _size_ (capacity).)
		Collections.sort(sources, VmStubComparator.getComparator(VmStubComparator.MEMORY, 
																VmStubComparator.CPU_CORES, 
																VmStubComparator.CORE_CAP, 
																VmStubComparator.CPU_IN_USE));
		Collections.reverse(sources);
		
		return sources;
	}

	/**
	 * Sorts Underutilized hosts in increasing order by <power efficiency, 
	 * CPU load>.
	 */
	@Override
	protected ArrayList<HostStub> orderSourceHosts(ArrayList<HostStub> underUtilized) {
		ArrayList<HostStub> sources = new ArrayList<HostStub>(underUtilized);
		
		// Sort Underutilized hosts in increasing order by <power efficiency, 
		// CPU load>.
		Collections.sort(sources, HostStubComparator.getComparator(HostStubComparator.EFFICIENCY, HostStubComparator.CPU_IN_USE));
		
		return sources;
	}

	/**
	 * Sorts Partially-utilized hosts in increasing order by <CPU utilization, 
	 * power efficiency>, and Underutilized hosts in decreasing order by 
	 * <CPU utilization, power efficiency>.
	 * 
	 * Returns Partially-utilized hosts, followed by Underutilized hosts.
	 */
	@Override
	protected ArrayList<HostStub> orderTargetHosts(ArrayList<HostStub> partiallyUtilized, ArrayList<HostStub> underUtilized) {
		ArrayList<HostStub> targets = new ArrayList<HostStub>();
		
		// Sort Partially-utilized in increasing order by <CPU utilization, 
		// power efficiency>.
		Collections.sort(partiallyUtilized, HostStubComparator.getComparator(HostStubComparator.CPU_UTIL));
		
		// Sort Underutilized hosts in decreasing order by <CPU utilization, 
		// power efficiency>.
		Collections.sort(underUtilized, HostStubComparator.getComparator(HostStubComparator.CPU_UTIL));
		Collections.reverse(underUtilized);
		
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		
		return targets;
	}

}
