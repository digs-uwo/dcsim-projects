package edu.uwo.csd.dcsim.projects.centralized.policies;

import java.util.ArrayList;
import java.util.Collection;

import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.action.MigrationAction;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.projects.centralized.events.VmRelocationEvent;

/**
 * Implements a greedy algorithm for VM Relocation. VMs are migrated out of 
 * Stressed hosts and into Partially-Utilized, Underutilized or Empty hosts.
 * 
 * Hosts are classified as Stressed, Partially-Utilized, Underutilized or 
 * Empty based on the hosts' average CPU utilization over the last window of 
 * time.
 * 
 * @author Gaston Keller
 *
 */
public abstract class VmRelocationPolicyGreedyReactive extends Policy {

	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	
	/**
	 * Creates an instance of VmRelocationPolicyGreedy.
	 */
	public VmRelocationPolicyGreedyReactive(double lowerThreshold, double upperThreshold, double targetUtilization) {
		addRequiredCapability(HostPoolManager.class);
		
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}
	
	/**
	 * Sorts the candidate VMs in the order in which they are to be considered 
	 * for VM Relocation.
	 */
	protected abstract ArrayList<VmStatus> orderSourceVms(ArrayList<VmStatus> sourceVms, HostData source);
	
	/**
	 * Sorts the source hosts in the order in which they are to be considered 
	 * for VM Relocation.
	 */
	protected abstract ArrayList<HostData> orderSourceHosts(ArrayList<HostData> stressed);
	
	/**
	 * Sorts the target hosts (Partially-Utilized, Underutilized and Empty) in 
	 * the order in which they are to be considered for VM Relocation.
	 */
	protected abstract ArrayList<HostData> orderTargetHosts(ArrayList<HostData> partiallyUtilized, ArrayList<HostData> underUtilized, ArrayList<HostData> empty);
	
	/**
	 * Performs the VM Relocation process.
	 */
	public void execute(VmRelocationEvent event) {
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		Collection<HostData> hosts = hostPool.getHosts();
		
		// Perform Stress Check in host that triggered the event.
		// If host is NOT stressed, finish.
		// Otherwise, perform VM Relocation process.
		if (!this.isStressed(hostPool.getHost(event.getHostId())))
			return;
		
		// Reset the sandbox host status to the current host status.
		for (HostData host : hosts) {
			host.resetSandboxStatusToCurrent();
		}
		
		// Categorize hosts.
		ArrayList<HostData> stressed = new ArrayList<HostData>();
		ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
		ArrayList<HostData> underUtilized = new ArrayList<HostData>();
		ArrayList<HostData> empty = new ArrayList<HostData>();
		
		this.classifyHosts(stressed, partiallyUtilized, underUtilized, empty, hosts);
		
		// Create (sorted) source and target lists.
		ArrayList<HostData> sources = this.orderSourceHosts(stressed);
		ArrayList<HostData> targets = this.orderTargetHosts(partiallyUtilized, underUtilized, empty);
		ArrayList<MigrationAction> migrations = new ArrayList<MigrationAction>();
		
		for (HostData source : sources) {
			
			boolean found = false;
			ArrayList<VmStatus> vmList = this.orderSourceVms(source.getCurrentStatus().getVms(), source);
			for (VmStatus vm : vmList) {
				
				for (HostData target : targets) {
					// Check that target host has at most 1 incoming migration pending, 
					// that target host is capable and has enough capacity left to host the VM, 
					// and also that it will not exceed the target utilization.
					if (target.getSandboxStatus().getIncomingMigrationCount() < 2 && 
						HostData.canHost(vm, target.getSandboxStatus(), target.getHostDescription()) && 
						(target.getSandboxStatus().getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
						
						// Modify host and vm states to record the future migration. Note that we 
						// can do this because we are using the designated 'sandbox' host status.
						source.getSandboxStatus().migrate(vm, target.getSandboxStatus());
						
						// Invalidate source and target status, as we know them to be incorrect until the next status update arrives.
						source.invalidateStatus(simulation.getSimulationTime());
						target.invalidateStatus(simulation.getSimulationTime());
						
						migrations.add(new MigrationAction(source.getHostManager(),
								source.getHost(),
								target.getHost(), 
								vm.getId()));
						
						found = true;
						break;
					}
				}
				
				if (found)
					break;
			}
		}
		
		// Trigger migrations.
		for (MigrationAction migration : migrations) {
			migration.execute(simulation, this);
		}
	}
	
	/**
	 * Classifies hosts as Stressed, Partially-Utilized, Underutilized or 
	 * Empty based on the hosts' average CPU utilization over the last window 
	 * of time.
	 */
	protected void classifyHosts(ArrayList<HostData> stressed, 
			ArrayList<HostData> partiallyUtilized, 
			ArrayList<HostData> underUtilized, 
			ArrayList<HostData> empty,
			Collection<HostData> hosts) {
		
		for (HostData host : hosts) {
			
			// Filter out hosts with a currently invalid status.
			if (host.isStatusValid()) {
					
				// Calculate host's avg CPU utilization over the last window of time.
				double avgCpuInUse = 0;
				int count = 0;
				for (HostStatus status : host.getHistory()) {
					// Only consider times when the host is powered on.
					if (status.getState() == Host.HostState.ON) {
						avgCpuInUse += status.getResourcesInUse().getCpu();
						++count;
					}
					else
						break;
				}
				if (count != 0) {
					avgCpuInUse = avgCpuInUse / count;
				}
				
				double avgCpuUtilization = Utility.roundDouble(avgCpuInUse / host.getHostDescription().getResourceCapacity().getCpu());
				
				// Classify hosts.
				if (host.getCurrentStatus().getVms().size() == 0) {
					empty.add(host);
				} else if (avgCpuUtilization < lowerThreshold) {
					underUtilized.add(host);
				} else if (avgCpuUtilization > upperThreshold) {
					stressed.add(host);
				} else {
					partiallyUtilized.add(host);
				}
			}
		}
	}
	
	/**
	 * Determines if host is stressed or not, based on its average
	 * CPU utilization over the last window of time.
	 */
	protected boolean isStressed(HostData host) {
		// Calculate host's avg CPU utilization over the last window of time.
		double avgCpuInUse = 0;
		int count = 0;
		for (HostStatus status : host.getHistory()) {
			// Only consider times when the host is powered on.
			if (status.getState() == Host.HostState.ON) {
				avgCpuInUse += status.getResourcesInUse().getCpu();
				++count;
			}
			else
				break;
		}
		if (count != 0) {
			avgCpuInUse = avgCpuInUse / count;
		}
		
		double avgCpuUtilization = Utility.roundDouble(avgCpuInUse / host.getHostDescription().getResourceCapacity().getCpu());
		if (avgCpuUtilization > upperThreshold)
			return true;
		
		return false;
	}
	
	@Override
	public void onInstall() {
		// TODO Auto-generated method stub
	}

	@Override
	public void onManagerStart() {
		// TODO Auto-generated method stub
	}

	@Override
	public void onManagerStop() {
		// TODO Auto-generated method stub
	}

}
