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
public final class StandardVmSizes {

	// Standard VM.
	// TODO: This value should be obtained from a config file.
	private static int cpu = 2500;		// 1 core * 2500 cpu units 
	private static int mem = 1024;		// 1 GB
	private static int bw = 12800;		// 100 Mb/s
	private static int storage = 1024;	// 1 GB
	
	/**
	 * Enforce non-instantiable class.
	 */
	private StandardVmSizes() {}
	
	/**
	 * Calculates the spare capacity of a Host, measured as the number of "standard VMs" that 
	 * can be placed in the Host to use up that spare capacity.
	 * 
	 * The Host must powered on (or powering on). Otherwise, the method returns 0.
	 * 
	 * This method does not check whether the Host's status is valid or not.
	 */
	public static double calculateSpareCapacity(HostData host) {
		Host.HostState state = host.getCurrentStatus().getState();
		if (state != Host.HostState.ON && state != Host.HostState.POWERING_ON)
			return 0;
		
		Resources spare = host.getHostDescription().getResourceCapacity().subtract(host.getCurrentStatus().getResourcesInUse());
		return Math.min(spare.getCpu() / ((double) cpu), Math.min(spare.getMemory() / ((double) mem), spare.getBandwidth() / ((double) bw)));
	}
	
	/**
	 * Converts a (resource) capacity value to its corresponding values of CPU, memory, 
	 * bandwidth and storage, based on the amount of resources required by a "standard VM".
	 */
	public static Resources convertCapacityToResources(double capacity) {
		return new Resources((int) (cpu * capacity), (int) (mem * capacity), (int) (bw * capacity), (int) (storage * capacity));
	}
	
}
