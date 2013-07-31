package edu.uwo.csd.dcsim.projects.hierarchical;

import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.HostData;

/**
 * 
 * 
 * @author Gaston Keller
 *
 */
public final class AverageVmSizes {

	// Average-sized VM.
	// TODO: This value should be obtained from a config file.
	private static double cpu = 2500;		// 1 core * 2500 cpu units 
	private static double mem = 1024;		// 1 GB
	private static double bw = 12800;		// 100 Mb/s
	private static long storage = 1024;	// 1 GB
	
	/**
	 * Enforce non-instantiable class.
	 */
	private AverageVmSizes() {}
	
	/**
	 * Calculates the spare capacity of a Host, measured as the number of "average-sized VMs"
	 * that can be placed in the Host to use up that spare capacity.
	 * 
	 * The Host must have a valid current status and be powered on (or powering on). Otherwise, 
	 * the method returns 0.
	 */
	public static double calculateSpareCapacity(HostData host) {
		// Check Host status. If invalid, we cannot calculate spare capacity.
		if (!host.isStatusValid())
			return 0;
		
		Host.HostState state = host.getCurrentStatus().getState();
		if (state != Host.HostState.ON && state != Host.HostState.POWERING_ON)
			return 0;
		
		Resources spare = host.getHostDescription().getResourceCapacity().subtract(host.getCurrentStatus().getResourcesInUse());
		return Math.min(spare.getCpu() / cpu, Math.min(spare.getMemory() / mem, spare.getBandwidth() / bw));
	}
	
	/**
	 * Converts a (resource) capacity value to its corresponding values of CPU, memory, 
	 * bandwidth and storage, based on the amount of resources required by an "average-sized VM".
	 */
	public static Resources convertCapacityToResources(double capacity) {
		return new Resources(cpu * capacity, (int) (mem * capacity), bw * capacity, (long) (storage * capacity));
	}
	
}
