package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.management.VMRelocationPolicyGreedy;
import edu.uwo.csd.dcsim.management.stub.*;

/**
 * Implements the following VM relocation policy:
 * 
 * - source hosts: only those hosts whose CPU utilization has remained above 
 *   the _upperThreshold_ *all the time* over the last CPU load monitoring 
 *   window are considered Stressed. These hosts are sorted in decreasing 
 *   order by CPU utilization;
 * - relocation candidates: VMs with less CPU load than the CPU load by which 
 *   the host is stressed are ignored. The rest of the VMs are sorted in 
 *   increasing order by CPU load;
 * - target hosts: sort Partially-utilized and Underutilized hosts in 
 *   decreasing order by <power efficiency, CPU utilization>, and Empty hosts 
 *   in decreasing order by <power efficiency, power state>. Return the hosts 
 *   in the following order: Partially-utilized and Underutilized hosts first, 
 *   followed by Empty hosts.
 * 
 * @author Gaston Keller
 *
 */
public class VMRelocationPolicyFFIDGreen extends VMRelocationPolicyGreedy {

	/**
	 * Creates a new instance of VMRelocationPolicyFFIDGreen.
	 */
	public VMRelocationPolicyFFIDGreen(DataCentre dc, DCUtilizationMonitor utilizationMonitor, double lowerThreshold, double upperThreshold, double targetUtilization) {
		super(dc, utilizationMonitor, lowerThreshold, upperThreshold, targetUtilization);
	}
	
	/**
	 * Sorts Stressed hosts in decreasing order by CPU utilization.
	 */
	@Override
	protected ArrayList<HostStub> orderSourceHosts(ArrayList<HostStub> stressed) {
		ArrayList<HostStub> sorted = new ArrayList<HostStub>(stressed);
		
		// Sort Stressed hosts in decreasing order by CPU utilization.
		Collections.sort(sorted, HostStubComparator.getComparator(HostStubComparator.CPU_UTIL));
		Collections.reverse(sorted);
		
		return sorted;
	}
	
	/**
	 * Sorts the relocation candidates in increasing order by CPU load, 
	 * previously removing from consideration those VMs with less CPU load 
	 * than the CPU load by which the host is stressed.
	 */
	@Override
	protected ArrayList<VmStub> orderSourceVms(ArrayList<VmStub> sourceVms) {
		ArrayList<VmStub> sorted = new ArrayList<VmStub>();
		
		// Remove VMs with less CPU load than the CPU load by which the source 
		// host is stressed.
		HostStub source = sourceVms.get(0).getHost();
		double cpuExcess = source.getCpuInUse() - source.getTotalCpu() * this.upperThreshold;
		for (VmStub vm : sourceVms)
			if (vm.getCpuInUse() >= cpuExcess)
				sorted.add(vm);
		
		if (!sorted.isEmpty())
			// Sort VMs in increasing order by CPU load.
			Collections.sort(sorted, new VmStubCpuInUseComparator());
		else {
			// Add original list of VMs and sort them in decreasing order by 
			// CPU load, so as to avoid trying to migrate the smallest VMs 
			// first (which would not help resolve the stress situation).
			sorted.addAll(sourceVms);
			Collections.sort(sorted, new VmStubCpuInUseComparator());
			Collections.reverse(sorted);
		}
		
		return sorted;
	}
	
	/**
	 * Sorts Partially-utilized and Underutilized hosts in decreasing order by 
	 * <power efficiency, CPU utilization>, and Empty hosts in decreasing 
	 * order by <power efficiency, power state>.
	 * 
	 * Returns Partially-utilized and Underutilized hosts first, followed by 
	 * Empty hosts.
	 */
	@Override
	protected ArrayList<HostStub> orderTargetHosts(
			ArrayList<HostStub> partiallyUtilized,
			ArrayList<HostStub> underUtilized, ArrayList<HostStub> empty) {
		
		ArrayList<HostStub> targets = new ArrayList<HostStub>();
		
		// Sort Partially-utilized and Underutilized hosts in decreasing order 
		// by <power efficiency, CPU utilization>.
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		Collections.sort(targets, HostStubComparator.getComparator(HostStubComparator.EFFICIENCY, HostStubComparator.CPU_UTIL));
		Collections.reverse(targets);
		
		// Sort Empty hosts in decreasing order by <power efficiency, 
		// power state>.
		Collections.sort(empty, HostStubComparator.getComparator(HostStubComparator.EFFICIENCY, HostStubComparator.PWR_STATE));
		Collections.reverse(empty);
		
		targets.addAll(empty);
		
		return targets;
	}
	
	/**
	 * Classifies hosts as Stressed, Partially-Utilized, Underutilized or 
	 * Empty based on the hosts' average CPU utilization over the last CPU 
	 * load monitoring window (see DCUtilizationMonitor).
	 * 
	 * Hosts in the Stressed category are parsed once more and demoted to the 
	 * Partially-Utilized category if any of their load measures in the last 
	 * monitoring window was *not* above the _upperThreshold_.
	 */
	@Override
	protected void classifyHosts(ArrayList<HostStub> stressed, 
			ArrayList<HostStub> partiallyUtilized, 
			ArrayList<HostStub> underUtilized, 
			ArrayList<HostStub> empty) {
		
		// Categorize hosts according to avg. CPU utilization over the last 
		// CPU load monitoring window (parent class method).
		super.classifyHosts(stressed, partiallyUtilized, underUtilized, empty);
		
		// Check hosts in the Stressed list so that only those hosts that were 
		// stressed *all the time* over the last CPU load monitoring window 
		// remain in the Stressed category.
		for (HostStub stub : stressed) {
			LinkedList<Double> hostUtilValues = this.utilizationMonitor.getHostInUse(stub.getHost());
			boolean stressedAllTime = true;
			for (Double x : hostUtilValues) {
				stressedAllTime = stressedAllTime && x > upperThreshold;
			}
			if (!stressedAllTime)
				stressed.remove(stub);
		}
	}

}
