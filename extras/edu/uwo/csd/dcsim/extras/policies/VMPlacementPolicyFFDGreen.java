package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.comparator.HostComparator;

/**
 * Implements a First Fit Decreasing algorithm for a Green Strategy, where it 
 * is the target hosts that are sorted in decreasing order by 
 * <power efficiency, CPU utilization>, and by <power efficiency, power state> 
 * if the hosts are empty.
 * 
 * @author Gaston Keller
 *
 */
public class VMPlacementPolicyFFDGreen extends VMPlacementPolicyGreedy {

	/**
	 * Creates an instance of VMPlacementPolicyFFDGreen.
	 */
	public VMPlacementPolicyFFDGreen(Simulation simulation, DataCentre dc, DCUtilizationMonitor utilizationMonitor, double lowerThreshold, double upperThreshold, double targetUtilization) {
		super(simulation, dc, utilizationMonitor, lowerThreshold, upperThreshold, targetUtilization);
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
	protected ArrayList<Host> orderTargetHosts(ArrayList<Host> partiallyUtilized,	ArrayList<Host> underUtilized, ArrayList<Host> empty) {
		ArrayList<Host> targets = new ArrayList<Host>();
		
		// Sort Partially-utilized and Underutilized hosts in decreasing order 
		// by <power efficiency, CPU utilization>.
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		Collections.sort(targets, HostComparator.getComparator(HostComparator.EFFICIENCY, HostComparator.CPU_UTIL));
		Collections.reverse(targets);
		
		// Sort Empty hosts in decreasing order by <power efficiency, 
		// power state>.
		Collections.sort(empty, HostComparator.getComparator(HostComparator.EFFICIENCY, HostComparator.PWR_STATE));
		Collections.reverse(empty);
		
		targets.addAll(empty);
		
		return targets;
	}

}
