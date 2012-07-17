package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

import edu.uwo.csd.dcsim.DCUtilizationMonitor;
import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.comparator.HostComparator;
import edu.uwo.csd.dcsim.management.VMPlacementPolicy;
import edu.uwo.csd.dcsim.vm.*;

/**
 * Implements a First Fit Decreasing algorithm for a Green Strategy, where it 
 * is the target hosts that are sorted in decreasing order by power efficiency 
 * (first factor) and CPU utilization (second factor), and by power efficiency 
 * (first factor) and power state (second factor) if the hosts are empty.
 * 
 * @author Gaston Keller
 *
 */
public class VMPlacementPolicyFFDGreen extends VMPlacementPolicy {

	protected DataCentre dc;
	protected DCUtilizationMonitor utilizationMonitor;
	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	
	public VMPlacementPolicyFFDGreen(Simulation simulation, DataCentre dc, DCUtilizationMonitor utilizationMonitor, double lowerThreshold, double upperThreshold, double targetUtilization) {
		super(simulation);
		this.dc = dc;
		this.utilizationMonitor = utilizationMonitor;
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}
	
	/**
	 * Places a new VM in the most efficient (first factor) and highly 
	 * utilized (second factor) host with enough spare capacity to take the VM 
	 * without exceeding the _targetUtilization_ threshold.
	 */
	@Override
	public boolean submitVM(VMAllocationRequest vmAllocationRequest) {
		
		ArrayList<Host> hostList = dc.getHosts();
		
		// Categorize hosts.
		ArrayList<Host> empty = new ArrayList<Host>();
		ArrayList<Host> underUtilized = new ArrayList<Host>();
		ArrayList<Host> partiallyUtilized = new ArrayList<Host>();
		
		for (Host host : hostList) {
			// Calculate host's avg CPU utilization in the last window of time.
			LinkedList<Double> hostUtilValues = this.utilizationMonitor.getHostInUse(host);
			double avgCpuInUse = 0;
			for (Double x : hostUtilValues) {
				avgCpuInUse += x;
			}
			avgCpuInUse = avgCpuInUse / this.utilizationMonitor.getWindowSize();
			
			double avgCpuUtilization = Utility.roundDouble(avgCpuInUse / host.getCpuManager().getTotalCpu());
			
			if (host.getVMAllocations().size() == 0) {
				empty.add(host);
			} else if (avgCpuUtilization < lowerThreshold) {
				underUtilized.add(host);
			} else if (avgCpuUtilization <= upperThreshold) {
				partiallyUtilized.add(host);
			}
		}
		
		// Create target hosts list.
		ArrayList<Host> targets = orderTargetHosts(partiallyUtilized, underUtilized, empty);
		
		Host allocatedHost = null;
		for (Host target : targets) {
			 if (target.isCapable(vmAllocationRequest.getVMDescription()) &&															// target is capable
			 	target.hasCapacity(vmAllocationRequest) &&																				// target has capacity
			 	(target.getCpuManager().getCpuInUse() + vmAllocationRequest.getCpu()) / target.getTotalCpu() <= targetUtilization) {	// target will not be stressed
				 
				 allocatedHost = target;
				 break;
			 }
		}
		
		return submitVM(vmAllocationRequest, allocatedHost);
	}

	/**
	 * Sorts the target hosts (Partially-utilized, Underutilized and Empty) in 
	 * decreasing order by power efficiency (first factor) and CPU utilization 
	 * (second factor), and by power efficiency (first factor) and power state 
	 * (second factor) if the hosts are empty.
	 * 
	 * Returns Partially-utilized and Underutilized hosts first, followed by 
	 * Empty hosts.
	 */
	private ArrayList<Host> orderTargetHosts(ArrayList<Host> partiallyUtilized,	ArrayList<Host> underUtilized, ArrayList<Host> empty) {
		ArrayList<Host> targets = new ArrayList<Host>();
		
		// Sort Partially-utilized and Underutilized hosts in decreasing order 
		// by power efficiency and CPU utilization.
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		Collections.sort(targets, HostComparator.getComparator(HostComparator.EFFICIENCY, HostComparator.CPU_UTIL));
		Collections.reverse(targets);
		
		// Sort Empty hosts in decreasing order by power efficiency and power 
		// state (on, suspended, off).
		Collections.sort(empty, HostComparator.getComparator(HostComparator.EFFICIENCY, HostComparator.PWR_STATE));
		Collections.reverse(empty);
		
		targets.addAll(empty);
		
		return targets;
	}

	/**
	 * Places a set of new VMs, one by one, in the most efficient (first 
	 * factor) and highly utilized (second factor) hosts with enough spare 
	 * capacity to take the VMs without exceeding the _targetUtilization_ 
	 * threshold.
	 */
	@Override
	public boolean submitVMs(ArrayList<VMAllocationRequest> vmAllocationRequests) {
		for (VMAllocationRequest request : vmAllocationRequests) {
			if (!submitVM(request)) {
				return false;
			}
		}
		
		return true;
	}

}
