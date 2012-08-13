package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.core.*;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Host.HostState;
import edu.uwo.csd.dcsim.management.action.MigrationAction;
import edu.uwo.csd.dcsim.management.stub.*;

/**
 * Implements the algorithm VM Placement Optimization found in "Optimal Online 
 * Deterministic Algorithms and Adaptive Heuristics for Energy and Performance 
 * Efficient Dynamic Consolidation of Virtual Machines in Cloud Data Centers", 
 * Anton Beloglazov and Rajkumar Buyya, Concurrency Computat.: Pract. Exper. 
 * 2011; 00:1-24.
 * 
 * The list of hosts is parsed twice. The first time, VMs are selected to be 
 * migrated away from Stressed hosts. At each Stressed host, VMs are sorted in 
 * increasing order by <MEM load> -- so as to select for migration the VM that 
 * will result in the lowest migration time -- and as many VMs are selected as 
 * needed to terminate the stress situation.
 * 
 * The second time, all the VMs hosted by Underutilized hosts are selected for 
 * migration.
 * 
 * After each iteration, the selected VMs are placed in the data centre 
 * following the Power Aware Best Fit Decreasing (PABFD) algorithm. VMs are 
 * sorted in decreasing order by <CPU load> and allocated to the host that 
 * provides the least increase in power consumption after the allocation.
 * 
 * All the migrations are triggered after the second placement procedure.
 * 
 * Empty hosts are switched off.
 * 
 * @author Gaston Keller
 *
 */
public class VMAllocationPolicyPABFD implements Daemon {

	protected DataCentre dc;
	protected DCUtilizationMonitor utilizationMonitor;
	protected double lowerThreshold;
	protected double upperThreshold;
	
	/**
	 * Creates an instance of VMAllocationPolicyPABFD.
	 */
	public VMAllocationPolicyPABFD(DataCentre dc, DCUtilizationMonitor utilizationMonitor, double lowerThreshold, double upperThreshold) {
		this.dc = dc;
		this.utilizationMonitor = utilizationMonitor;
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
	}

	@Override
	public void run(Simulation simulation) {
		ArrayList<HostStub> hostList = HostStub.createHostStubList(dc.getHosts());
		ArrayList<VmStub> migrationList = new ArrayList<VmStub>();
		
		for (HostStub host : hostList) {
			// Sort VMs in decreasing order by CPU load.
			ArrayList<VmStub> vmList = new ArrayList<VmStub>(host.getVms());
			Collections.sort(vmList, VmStubComparator.getComparator(VmStubComparator.CPU_IN_USE));
			Collections.reverse(vmList);
			
			double hUtil = host.getCpuInUse();
			double bestFitUtil = Double.MAX_VALUE;
			double upperThresholdValue  = host.getTotalCpu() * upperThreshold;
			double lowerThresholdValue = host.getTotalCpu() * lowerThreshold;
			
			// Select VMs to migrate away from stressed hosts.
			while (hUtil > upperThresholdValue) {
				VmStub bestFitVm = null;
				for (VmStub vm : vmList) {
					if (vm.getCpuInUse() > hUtil - upperThresholdValue) {
						double t = vm.getCpuInUse() - hUtil + upperThresholdValue;
						if (t < bestFitUtil) {
							bestFitUtil = t;
							bestFitVm = vm;
						}
					} else {
						if (bestFitUtil == Double.MAX_VALUE) {
							bestFitVm = vm;
						}
						break;
					}
				}
				hUtil = hUtil - bestFitVm.getCpuInUse();
				migrationList.add(bestFitVm);
				vmList.remove(bestFitVm);
			}
			
			// Select VMs (all) to migrate away from Underutilized hosts.
			if (hUtil < lowerThresholdValue) {
				migrationList.addAll(vmList);
				vmList.clear();
			}
		}
		
		// Place VMs.
		this.placeVMs(hostList, migrationList, simulation);
		
		// Shut down Empty hosts.
		for (HostStub host : hostList) {
			// Ensure that the host is not involved in any migration and is 
			// not powering on.
			if (host.getHost().getVMAllocations().size() == 0 && 
					host.getIncomingMigrationCount() == 0 && 
					host.getOutgoingMigrationCount() == 0 && 
					host.getHost().getState() != HostState.POWERING_ON) {
				
				simulation.sendEvent(new Event(Host.HOST_POWER_OFF_EVENT, simulation.getSimulationTime(), this,host.getHost()));
			}
		}
	}
	
	/**
	 * Estimates power consumption of a host after receiving an incoming VM.
	 */
	protected double estimatePower(HostStub host, VmStub vm) {
		double powerBefore = host.getHost().getCurrentPowerConsumption();
		double powerAfter = host.getHost().getPowerModel().getPowerConsumption(host.getCpuUtilization(vm));
		return powerAfter - powerBefore;
	}
	
	/**
	 * Determines if the host is Empty.
	 */
	protected boolean isHostEmpty(HostStub stub) {
		if (stub.getHost().getVMAllocations().size() == 0)
			return true;
		return false;
	}
	
	/**
	 * Implements the Power Aware Best Fit Decreasing (PABFD) algorithm for VM 
	 * Placement. VMs are sorted in decreasing order by <CPU load> and 
	 * allocated to the host that provides the least increase in power 
	 * consumption after the allocation.
	 */
	protected void placeVMs(ArrayList<HostStub> hostList, ArrayList<VmStub> vmList, Simulation simulation) {
		ArrayList<MigrationAction> migrations = new ArrayList<MigrationAction>();
		
		// Sort VMs in decreasing order by CPU load.
		Collections.sort(vmList, VmStubComparator.getComparator(VmStubComparator.CPU_IN_USE));
		Collections.reverse(vmList);
		
		for (VmStub vm : vmList) {
			double minPower = Double.MAX_VALUE;
			HostStub allocatedHost = null;
			for (HostStub target : hostList) {
				if (target.hasCapacity(vm) &&												// target has capacity
						(target.getCpuInUse(vm) <= target.getTotalCpu()) &&					// target has cpu capacity
						target.getHost().isCapable(vm.getVM().getVMDescription())) {		// target is capable
					
					double power = this.estimatePower(target, vm);
					if (power < minPower) {
						allocatedHost = target;
						minPower = power;
					}
				}
			}
			if (allocatedHost != null) {
				migrations.add(new MigrationAction(vm.getHost(), allocatedHost, vm));
				vm.getHost().migrate(vm, allocatedHost);
			}
		}
		
		// Trigger migrations.
		for (MigrationAction migration : migrations) {
			migration.execute(simulation, this);
		}
	}
	
	@Override
	public void onStart(Simulation simulation) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStop(Simulation simulation) {
		// TODO Auto-generated method stub
		
	}

}
