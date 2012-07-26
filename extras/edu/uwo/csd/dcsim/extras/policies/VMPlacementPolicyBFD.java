package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.DCUtilizationMonitor;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.VMPlacementPolicy;
import edu.uwo.csd.dcsim.vm.*;

/**
 * Implements a Best Fit Decreasing (BFD) algorithm, where VMs are sorted in 
 * decreasing order by overall requested capacity and placed in the most power 
 * efficient host available (power efficiency calculated as TotalCpuCapacity 
 * over PowerConsumptionWhenFullyLoaded.
 * 
 * @author Gaston Keller
 *
 */
public class VMPlacementPolicyBFD extends VMPlacementPolicy {

	protected DCUtilizationMonitor utilizationMonitor;
	
	public VMPlacementPolicyBFD(Simulation simulation, DCUtilizationMonitor utilizationMonitor) {
		super(simulation);
		this.utilizationMonitor = utilizationMonitor;
	}
	
	@Override
	public boolean submitVM(VMAllocationRequest vmAllocationRequest) {
		ArrayList<VMAllocationRequest> vmAllocationRequests = new ArrayList<VMAllocationRequest>();
		vmAllocationRequests.add(vmAllocationRequest);
		
		return submitVMs(vmAllocationRequests);
	}

	@Override
	public boolean submitVMs(ArrayList<VMAllocationRequest> vmAllocationRequests) {
		boolean success = true;
		
		// Sort VMs in decreasing order by overall capacity.
		Collections.sort(vmAllocationRequests, new VMAllocationRequestCapacityComparator());
		Collections.reverse(vmAllocationRequests);
		
		for (VMAllocationRequest vmAllocationRequest : vmAllocationRequests) {
			Host allocatedHost = null;
			double maxPowerE = Double.MIN_VALUE;
			
			for (Host host : datacentre.getHosts()) {
				// Check whether host has enough resources for VM.
				if (host.isCapable(vmAllocationRequest.getVMDescription()) &&
						host.getMemoryManager().hasCapacity(vmAllocationRequest)
						&& host.getBandwidthManager().hasCapacity(vmAllocationRequest)
						&& host.getStorageManager().hasCapacity(vmAllocationRequest)
						&& host.getCpuManager().getAvailableAllocation() >= vmAllocationRequest.getCpu()) {
					
					double powerEfficiency = host.getTotalCpu() / host.getPowerModel().getPowerConsumption(1);
					
					if (powerEfficiency > maxPowerE) {
						allocatedHost = host;
						maxPowerE = powerEfficiency;
					}
				}
			}
			
			if (allocatedHost != null) {
				// Allocate VM to host.
				success = success & submitVM(vmAllocationRequest, allocatedHost);
			}
		}
		
		return success;
	}

}
