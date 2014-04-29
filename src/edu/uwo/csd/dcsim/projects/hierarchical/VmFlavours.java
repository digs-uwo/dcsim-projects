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
public final class VmFlavours {

//	public static final int[] VM_SIZES = {1500, 2500, 3000, 3000};
//	public static final int[] VM_CORES = {1, 1, 1, 2};
//	public static final int[] VM_RAM = {512, 1024, 1024, 1024};
//	public static final int N_VM_SIZES = 4;

	// Standard VM.
	// TODO: This value should be obtained from a config file.
	private static int cpu = 2500;		// 1 core * 2500 cpu units 
	private static int mem = 1024;		// 1 GB
	private static int bw = 12800;		// 100 Mb/s
	private static int storage = 1024;	// 1 GB
	
	/**
	 * Enforce non-instantiable class.
	 */
	private VmFlavours() {}
	
	/**
	 * Standard VM sizes defined by OpenStack.
	 */
	
	public static Resources tiny() {
		//return new Resources(1, 2500, 1024, 12800, 1024);
		return new Resources(1, 2400, 1024, 12800, 1024);
	}
	
	public static Resources small() {
		return new Resources(1, 2500, 2048, 12800, 1024);
	}
	
	public static Resources medium() {
		return new Resources(2, 2500, 4096, 12800, 1024);
	}
	
	public static Resources large() {
		return new Resources(4, 2500, 8192, 12800, 1024);
	}
	
	public static Resources xlarge() {
		return new Resources(8, 2500, 16384, 12800, 1024);
	}
	
	/**
	 * Extra VM sizes.
	 */
	
	public static Resources xtiny() {
		//return new Resources(1, 2500, 512, 12800, 1024);
		return new Resources(1, 2400, 512, 12800, 1024);
	}
	
	public static Resources manfi1() {
		return new Resources(1, 1500, 512, 12800, 1024);
	}
	
	public static Resources manfi2() {
		//return new Resources(1, 2500, 1024, 12800, 1024);
		return new Resources(1, 2400, 1024, 12800, 1024);
	}
	
	public static Resources manfi3() {
		//return new Resources(2, 2500, 1024, 12800, 1024);
		return new Resources(2, 2400, 1024, 12800, 1024);
	}
	
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
