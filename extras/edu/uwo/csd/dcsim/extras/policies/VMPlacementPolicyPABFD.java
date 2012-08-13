package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.VMPlacementPolicy;
import edu.uwo.csd.dcsim.vm.VMAllocationRequest;
import edu.uwo.csd.dcsim.vm.VMAllocationRequestCpuUtilComparator;

/**
 * Implements the Power Aware Best Fit Decreasing (PABFD) algorithm found in 
 * "Optimal Online Deterministic Algorithms and Adaptive Heuristics for Energy 
 * and Performance Efficient Dynamic Consolidation of Virtual Machines in 
 * Cloud Data Centers", Anton Beloglazov and Rajkumar Buyya, Concurrency 
 * Computat.: Pract. Exper. 2011; 00:1-24.
 * 
 * This VM Placement algorithm sorts VMs in decreasing order by <CPU load> and 
 * allocates each VM to the host that provides the least increase in power 
 * consumption after the allocation.
 * 
 * @author Gaston Keller
 *
 */
public class VMPlacementPolicyPABFD extends VMPlacementPolicy {

	protected DataCentre dc;
	protected DCUtilizationMonitor utilizationMonitor;
	protected double lowerThreshold;
	protected double upperThreshold;
	
	/**
	 * Creates an instance of VMPlacementPolicyPABFD.
	 */
	public VMPlacementPolicyPABFD(Simulation simulation, DataCentre dc, DCUtilizationMonitor utilizationMonitor, double lowerThreshold, double upperThreshold) {
		super(simulation);
		this.dc = dc;
		this.utilizationMonitor = utilizationMonitor;
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
	}
	
	/**
	 * Estimates power consumption of a host after receiving an incoming VM.
	 */
	protected double estimatePower(Host host, VMAllocationRequest vmAllocationRequest) {
		double powerBefore = host.getCurrentPowerConsumption();
		double cpuUtilAfter = (host.getCpuManager().getCpuInUse() + vmAllocationRequest.getCpu()) / host.getTotalCpu();
		double powerAfter = host.getPowerModel().getPowerConsumption(cpuUtilAfter);
		return powerAfter - powerBefore;
	}
	
	/**
	 * Implements the Power Aware Best Fit Decreasing (PABFD) algorithm for VM 
	 * Placement. The VM is allocated to the host that provides the least 
	 * increase in power consumption after the allocation.
	 */
	@Override
	public boolean submitVM(VMAllocationRequest vmAllocationRequest) {
		ArrayList<Host> hostList = dc.getHosts();
		
		double minPower = Double.MAX_VALUE;
		Host allocatedHost = null;
		for (Host target : hostList) {
			if (target.hasCapacity(vmAllocationRequest) && 														// target has capacity
				target.getCpuManager().getCpuInUse() + vmAllocationRequest.getCpu() < target.getTotalCpu() && 	// target has CPU capacity
				target.isCapable(vmAllocationRequest.getVMDescription())) {										// target is capable
				
				double power = this.estimatePower(target, vmAllocationRequest);
				if (power < minPower) {
					allocatedHost = target;
					minPower = power;
				}
			}
		}
		
		if (allocatedHost != null)
			return submitVM(vmAllocationRequest, allocatedHost);
		else
			return false;
	}

	/**
	 * Implements the Power Aware Best Fit Decreasing (PABFD) algorithm for VM 
	 * Placement. VMs are sorted in decreasing order by <CPU load> and 
	 * allocated to the host that provides the least increase in power 
	 * consumption after the allocation.
	 */
	@Override
	public boolean submitVMs(ArrayList<VMAllocationRequest> vmAllocationRequests) {
		// Sort VM allocation requests in decreasing order by <CPU load>.
		Collections.sort(vmAllocationRequests, new VMAllocationRequestCpuUtilComparator());
		Collections.reverse(vmAllocationRequests);
		
		for (VMAllocationRequest request : vmAllocationRequests) {
			if (!submitVM(request)) {
				return false;
			}
		}
		
		return true;
	}

}
