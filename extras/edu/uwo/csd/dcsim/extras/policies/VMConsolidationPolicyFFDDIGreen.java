package edu.uwo.csd.dcsim.extras.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.DCUtilizationMonitor;
import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.management.stub.HostStub;
import edu.uwo.csd.dcsim.management.stub.HostStubComparator;
import edu.uwo.csd.dcsim.management.stub.VmStub;
import edu.uwo.csd.dcsim.management.stub.VmStubCpuInUseComparator;

/**
 * @author Gaston Keller
 *
 */
public class VMConsolidationPolicyFFDDIGreen extends VMConsolidationPolicyGreedy {

	/**
	 * Creates a new instance of VMConsolidationPolicyFFDDIGreen.
	 */
	public VMConsolidationPolicyFFDDIGreen(DataCentre dc, DCUtilizationMonitor utilizationMonitor, double lowerThreshold, double upperThreshold, double targetUtilization) {
		super(dc, utilizationMonitor, lowerThreshold, upperThreshold, targetUtilization);
	}

	@Override
	protected ArrayList<VmStub> orderSourceVms(ArrayList<VmStub> sourceVms) {
		ArrayList<VmStub> sources = new ArrayList<VmStub>(sourceVms);
		Collections.sort(sources, new VmStubCpuInUseComparator());
		Collections.reverse(sources);
		return sources;
	}
	
	/**
	 * Sorts Underutilized hosts in increasing order by power efficiency 
	 * (first factor) and CPU load (second factor).
	 */
	@Override
	protected ArrayList<HostStub> orderSourceHosts(ArrayList<HostStub> underUtilized) {
		ArrayList<HostStub> sources = new ArrayList<HostStub>(underUtilized);
		
		// Sort Underutilized hosts in increasing order by power efficiency 
		// and CPU load.
		Collections.sort(sources, HostStubComparator.getComparator(HostStubComparator.EFFICIENCY, HostStubComparator.CPU_IN_USE));
		
		return sources;
	}

	/**
	 * Sorts Partially-utilized and Underutilized hosts in decreasing order by 
	 * power efficiency (first factor) and CPU load (second factor).
	 */
	@Override
	protected ArrayList<HostStub> orderTargetHosts(ArrayList<HostStub> partiallyUtilized, ArrayList<HostStub> underUtilized) {
		ArrayList<HostStub> targets = new ArrayList<HostStub>();
		
		// Sort Partially-utilized and Underutilized hosts in decreasing order 
		// by power efficiency and CPU load.
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		Collections.sort(targets, HostStubComparator.getComparator(HostStubComparator.EFFICIENCY, HostStubComparator.CPU_IN_USE));
		Collections.reverse(targets);
		
		return targets;
	}

}
