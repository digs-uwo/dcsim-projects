package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

import edu.uwo.csd.dcsim.DCUtilizationMonitor;
import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.VMPlacementPolicy;
import edu.uwo.csd.dcsim.management.action.MigrationAction;
import edu.uwo.csd.dcsim.management.stub.HostStub;
import edu.uwo.csd.dcsim.management.stub.HostStubCpuInUseComparator;
import edu.uwo.csd.dcsim.management.stub.HostStubPowerStateComparator;
import edu.uwo.csd.dcsim.vm.*;

/**
 * @author gkeller2
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

	private ArrayList<Host> orderTargetHosts(ArrayList<Host> partiallyUtilized,	ArrayList<Host> underUtilized, ArrayList<Host> empty) {
		ArrayList<Host> targets = new ArrayList<Host>();
		
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		//Collections.sort(targets, new HostComparator());
		
		// Sort Partially-utilized hosts in decreasing order by CPU utilization.
		//Collections.sort(partiallyUtilized, new HostStubCpuInUseComparator());
		Collections.reverse(partiallyUtilized);
		
		// Sort Underutilized hosts in decreasing order by CPU utilization.
		//Collections.sort(underUtilized, new HostStubCpuInUseComparator());
		Collections.reverse(underUtilized);
		
		// Sort Empty hosts in decreasing order by power state 
		// (on, suspended, off).
		//Collections.sort(empty, new HostStubPowerStateComparator());
		Collections.reverse(empty);
		
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		targets.addAll(empty);
		
		return targets;
	}

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
