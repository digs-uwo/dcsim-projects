package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.management.*;

/**
 * Implements the following VM Placement policy:
 * 
 * - target hosts: sort Partially-Utilized hosts in increasing order by 
 *   <CPU utilization, power efficiency>, Underutilized hosts in decreasing 
 *   order by <CPU utilization, power efficiency>, and Empty hosts in 
 *   decreasing order by <power efficiency, power state>. Return the hosts in 
 *   the following order: Partially-Utilized, Underutilized, and Empty.
 * 
 * @author Gaston Keller
 *
 */
public class VmPlacementPolicyFFMHybrid extends VmPlacementPolicyLevel0 {

	/**
	 * Creates an instance of VmPlacementPolicyFFMHybrid.
	 */
	public VmPlacementPolicyFFMHybrid(AutonomicManager target, double lowerThreshold, double upperThreshold, double targetUtilization) {
		super(target, lowerThreshold, upperThreshold, targetUtilization);
	}
	
	/**
	 * Sorts Partially-Utilized hosts in increasing order by <CPU utilization, power efficiency>, 
	 * Underutilized hosts in decreasing order by <CPU utilization, power efficiency>, 
	 * and Empty hosts in decreasing order by <power efficiency, power state>.
	 * 
	 * Returns Partially-Utilized, Underutilized and Empty hosts in that order.
	 */
	@Override
	protected ArrayList<HostData> orderTargetHosts(ArrayList<HostData> partiallyUtilized, ArrayList<HostData> underUtilized, ArrayList<HostData> empty) {
		ArrayList<HostData> targets = new ArrayList<HostData>();
		
		// Sort Partially-utilized in increasing order by <CPU utilization, power efficiency>.
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
