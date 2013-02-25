package edu.uwo.csd.dcsim.projects.im2013.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.HostDataComparator;

/**
 * Implements a First Fit Decreasing algorithm for the Power Strategy, where it 
 * is the target hosts that are sorted in decreasing order by 
 * <power efficiency, CPU utilization>, and by <power efficiency, power state> 
 * if the hosts are empty.
 * 
 * @author Gaston Keller
 *
 */
public class VmPlacementPolicyFFDPower extends VmPlacementPolicyGreedy {

	/**
	 * Creates an instance of VmPlacementPolicyFFDPower.
	 */
	public VmPlacementPolicyFFDPower(double lowerThreshold, double upperThreshold, double targetUtilization) {
		super(lowerThreshold, upperThreshold, targetUtilization);
	}

	/**
	 * Sorts the target hosts (Partially-utilized, Underutilized and Empty) in 
	 * decreasing order by <power efficiency, CPU utilization>, and by 
	 * <power efficiency, power state> if the hosts are empty.
	 * 
	 * Returns Partially-utilized and Underutilized hosts first, followed by 
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

}
