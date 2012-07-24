package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.LinkedList;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.VMPlacementPolicy;
import edu.uwo.csd.dcsim.vm.VMAllocationRequest;

/**
 * Implements a greedy algorithm for VM Placement. VMs are placed in the first 
 * non-stressed host with enough spare capacity to take the VM without 
 * exceeding the _targetUtilization_ threshold.
 * 
 * Hosts are classified as Stressed, Partially-Utilized, Underutilized or 
 * Empty based on the hosts' average CPU utilization over the last CPU load 
 * monitoring window (see DCUtilizationMonitor).
 * 
 * @author Gaston Keller
 *
 */
public abstract class VMPlacementPolicyGreedy extends VMPlacementPolicy {

	protected DataCentre dc;
	protected DCUtilizationMonitor utilizationMonitor;
	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	
	/**
	 * Creates an instance of VMPlacementPolicyGreedy.
	 */
	public VMPlacementPolicyGreedy(Simulation simulation, DataCentre dc, DCUtilizationMonitor utilizationMonitor, double lowerThreshold, double upperThreshold, double targetUtilization) {
		super(simulation);
		this.dc = dc;
		this.utilizationMonitor = utilizationMonitor;
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}

	/**
	 * Sorts the target hosts (Partially-utilized, Underutilized and Empty) in 
	 * the order in which they are to be considered for the VM Placement.
	 */
	protected abstract ArrayList<Host> orderTargetHosts(ArrayList<Host> partiallyUtilized,	ArrayList<Host> underUtilized, ArrayList<Host> empty);
	
	/**
	 * Classifies hosts as Partially-Utilized, Underutilized or Empty based on 
	 * the hosts' average CPU utilization over the last CPU load monitoring 
	 * window (see DCUtilizationMonitor).
	 */
	protected void classifyHosts(ArrayList<Host> partiallyUtilized, ArrayList<Host> underUtilized, ArrayList<Host> empty) {
		ArrayList<Host> hostList = dc.getHosts();
		
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
	}

	/**
	 * Places a VM in the first non-stressed host with enough spare capacity 
	 * to take the VM without exceeding the _targetUtilization_ threshold.
	 */
	@Override
	public boolean submitVM(VMAllocationRequest vmAllocationRequest) {
		
		// Categorize hosts.
		ArrayList<Host> partiallyUtilized = new ArrayList<Host>();
		ArrayList<Host> underUtilized = new ArrayList<Host>();
		ArrayList<Host> empty = new ArrayList<Host>();
		
		this.classifyHosts(partiallyUtilized, underUtilized, empty);
		
		// Create target hosts list.
		ArrayList<Host> targets = this.orderTargetHosts(partiallyUtilized, underUtilized, empty);
		
		Host allocatedHost = null;
		for (Host target : targets) {
			 if (target.isCapable(vmAllocationRequest.getVMDescription()) &&															// target is capable
			 	target.hasCapacity(vmAllocationRequest) &&																				// target has capacity
			 	(target.getCpuManager().getCpuInUse() + vmAllocationRequest.getCpu()) / target.getTotalCpu() <= targetUtilization) {	// target will not be stressed
				 
				 allocatedHost = target;
				 break;
			 }
		}
		
		if (allocatedHost != null)
			return submitVM(vmAllocationRequest, allocatedHost);
		else
			return false;
	}

	/**
	 * Places a set of VMs, one by one, in the available hosts in the data 
	 * centre.
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
