package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.comparator.HostComparator;

/**
 * Implements a First Fit algorithm for a Balanced Strategy, where the target 
 * hosts are sorted as follows: Partially-utilized hosts in increasing 
 * order by <CPU utilization, power efficiency>, followed by Underutilized 
 * hosts in decreasing order by <CPU utilization, power efficiency>, and 
 * finally Empty hosts in decreasing order by <power efficiency, power state>.
 * 
 * @author Gaston Keller
 *
 */
public class VMPlacementPolicyFFMBalanced extends VMPlacementPolicyGreedy {

	/**
	 * Creates an instance of VMPlacementPolicyFFMBalanced.
	 */
	public VMPlacementPolicyFFMBalanced(Simulation simulation, DataCentre dc, DCUtilizationMonitor utilizationMonitor, double lowerThreshold, double upperThreshold, double targetUtilization) {
		super(simulation, dc, utilizationMonitor, lowerThreshold, upperThreshold, targetUtilization);
	}

	/**
	 * Sorts Partially-utilized hosts in increasing order by <CPU utilization, 
	 * power efficiency>, Underutilized hosts in decreasing order by 
	 * <CPU utilization, power efficiency>, and Empty hosts in decreasing 
	 * order by <power efficiency, power state>.
	 * 
	 * Returns Partially-utilized, Underutilized and Empty hosts in that order.
	 */
	@Override
	protected ArrayList<Host> orderTargetHosts(ArrayList<Host> partiallyUtilized, ArrayList<Host> underUtilized, ArrayList<Host> empty) {
		ArrayList<Host> targets = new ArrayList<Host>();
		
		// Sort Partially-utilized in increasing order by <CPU utilization, 
		// power efficiency>.
		Collections.sort(partiallyUtilized, HostComparator.getComparator(HostComparator.CPU_UTIL, HostComparator.EFFICIENCY));
		
		// Sort Underutilized hosts in decreasing order by <CPU utilization, 
		// power efficiency>.
		Collections.sort(underUtilized, HostComparator.getComparator(HostComparator.CPU_UTIL, HostComparator.EFFICIENCY));
		Collections.reverse(underUtilized);
		
		// Sort Empty hosts in decreasing order by <power efficiency, 
		// power state>.
		Collections.sort(empty, HostComparator.getComparator(HostComparator.EFFICIENCY, HostComparator.PWR_STATE));
		Collections.reverse(empty);
		
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		targets.addAll(empty);
		
		return targets;
	}

}
