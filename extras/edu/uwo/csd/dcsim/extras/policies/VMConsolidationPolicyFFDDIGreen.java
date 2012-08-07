package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.DCUtilizationMonitor;
import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.management.stub.*;

/**
 * Implements the following VM consolidation policy:
 * 
 * - relocation candidates: sort VMs in decreasing order by <overall capacity, 
 *   CPU load>;
 * - target hosts: sort Partially-utilized and Underutilized hosts in 
 *   decreasing order by <power efficiency, CPU utilization>.
 * - source hosts: sort Underutilized hosts in increasing order by 
 *   <power efficiency, CPU utilization>.
 * 
 * @author Gaston Keller
 *
 */
public class VMConsolidationPolicyFFDDIGreen extends VMConsolidationPolicyGreedy {

	/**
	 * Creates a new instance of VMConsolidationPolicyFFDDIGreen.
	 */
	public VMConsolidationPolicyFFDDIGreen(DataCentre dc, DCUtilizationMonitor utilizationMonitor, double lowerThreshold, double upperThreshold, double targetUtilization) {
		super(dc, utilizationMonitor, lowerThreshold, upperThreshold, targetUtilization);
	}

	/**
	 * Sorts VMs in decreasing order by <overall capacity, CPU load>, so as to 
	 * place the _biggest_ VMs first.
	 * 
	 * (Note: since CPU can be oversubscribed, but memory can't, memory takes 
	 * priority over CPU when comparing VMs by _size_ (capacity).)
	 */
	@Override
	protected ArrayList<VmStub> orderSourceVms(ArrayList<VmStub> sourceVms) {
		ArrayList<VmStub> sources = new ArrayList<VmStub>(sourceVms);
		
		// Sort VMs in decreasing order by <overall capacity, CPU load>.
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
	 * CPU utilization>.
	 */
	@Override
	protected ArrayList<HostStub> orderSourceHosts(ArrayList<HostStub> underUtilized) {
		ArrayList<HostStub> sources = new ArrayList<HostStub>(underUtilized);
		
		// Sort Underutilized hosts in increasing order by <power efficiency, 
		// CPU utilization>.
		Collections.sort(sources, HostStubComparator.getComparator(HostStubComparator.EFFICIENCY, HostStubComparator.CPU_UTIL));
		
		return sources;
	}

	/**
	 * Sorts Partially-utilized and Underutilized hosts in decreasing order by 
	 * <power efficiency, CPU utilization>.
	 */
	@Override
	protected ArrayList<HostStub> orderTargetHosts(ArrayList<HostStub> partiallyUtilized, ArrayList<HostStub> underUtilized) {
		ArrayList<HostStub> targets = new ArrayList<HostStub>();
		
		// Sort Partially-utilized and Underutilized hosts in decreasing order 
		// by <power efficiency, CPU utilization>.
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		Collections.sort(targets, HostStubComparator.getComparator(HostStubComparator.EFFICIENCY, HostStubComparator.CPU_UTIL));
		Collections.reverse(targets);
		
		return targets;
	}

}
