package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;

import edu.uwo.csd.dcsim.DCUtilizationMonitor;
import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.core.*;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.action.MigrationAction;
import edu.uwo.csd.dcsim.management.stub.*;

/**
 * Implements a greedy algorithm for VM Consolidation. VMs are migrated out of 
 * underUtilized hosts and into other underUtilized hosts (though one host 
 * cannot be used both as consolidation source and target) and 
 * Partially-Utilized hosts.
 * 
 * Hosts are classified as Stressed, Partially-Utilized, Underutilized or 
 * Empty based on the hosts' average CPU utilization over the last CPU load 
 * monitoring window (see DCUtilizationMonitor).
 * 
 * There's no limit to the number of VMs that can be migrated out of a host.
 * 
 * @author Gaston Keller
 *
 */
public abstract class VMConsolidationPolicyGreedy implements Daemon {

	protected DataCentre dc;
	protected DCUtilizationMonitor utilizationMonitor;
	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	
	/**
	 * Creates an instance of VMConsolidationPolicyGreedy.
	 */
	public VMConsolidationPolicyGreedy(DataCentre dc, DCUtilizationMonitor utilizationMonitor, double lowerThreshold, double upperThreshold, double targetUtilization) {
		this.dc = dc;
		this.utilizationMonitor = utilizationMonitor;
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}

	protected abstract ArrayList<VmStub> orderSourceVms(ArrayList<VmStub> sourceVms);
	
	protected abstract ArrayList<HostStub> orderSourceHosts(ArrayList<HostStub> underUtilized);
	
	protected abstract ArrayList<HostStub> orderTargetHosts(ArrayList<HostStub> partiallyUtilized, ArrayList<HostStub> underUtilized);
	
	@Override
	public void run(Simulation simulation) {
		// Categorize hosts.
		ArrayList<HostStub> stressed = new ArrayList<HostStub>();
		ArrayList<HostStub> partiallyUtilized = new ArrayList<HostStub>();
		ArrayList<HostStub> underUtilized = new ArrayList<HostStub>();
		ArrayList<HostStub> empty = new ArrayList<HostStub>();
		
		this.classifyHosts(stressed, partiallyUtilized, underUtilized, empty);
		
		// Create (sorted) source and target lists.
		ArrayList<HostStub> sources = this.orderSourceHosts(underUtilized);
		ArrayList<HostStub> targets = this.orderTargetHosts(partiallyUtilized, underUtilized);
		
		HashSet<HostStub> usedSources = new HashSet<HostStub>();
		HashSet<HostStub> usedTargets = new HashSet<HostStub>();
		ArrayList<MigrationAction> migrations = new ArrayList<MigrationAction>();
		for (HostStub source : sources) {
			if (!usedTargets.contains(source)) { 	// Check that the source host hasn't been used as a target.
			
				ArrayList<VmStub> vmList = this.orderSourceVms(source.getVms());
				for (VmStub vm : vmList) {
					for (HostStub target : targets) {
						 if (source != target &&
								 !usedSources.contains(target) &&											// Check that the target host hasn't been used as a source.
								 target.hasCapacity(vm) &&													// Target host has capacity.
								 (target.getCpuInUse(vm) / target.getTotalCpu()) <= targetUtilization &&	// Target host will not exceed target utilization.
								 target.getHost().isCapable(vm.getVM().getVMDescription())) {				// Target host is capable.
							 
							 source.migrate(vm, target);
							 migrations.add(new MigrationAction(source, target, vm));
							 
							 usedTargets.add(target);
							 usedSources.add(source);
							 break;
						 }
					}
				}
			}
		}
		
		// Trigger migrations.
		for (MigrationAction migration : migrations) {
			migration.execute(simulation, this);
		}
	}

	/**
	 * Classifies hosts as Stressed, Partially-Utilized, Underutilized or 
	 * Empty based on the hosts' average CPU utilization over the last CPU 
	 * load monitoring window (see DCUtilizationMonitor).
	 */
	protected void classifyHosts(ArrayList<HostStub> stressed, 
			ArrayList<HostStub> partiallyUtilized, 
			ArrayList<HostStub> underUtilized, 
			ArrayList<HostStub> empty) {
		
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
				empty.add(new HostStub(host));
			} else if (avgCpuUtilization < lowerThreshold) {
				underUtilized.add(new HostStub(host));
			} else if (avgCpuUtilization > upperThreshold) {
				stressed.add(new HostStub(host));
			} else {
				partiallyUtilized.add(new HostStub(host));
			}
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
