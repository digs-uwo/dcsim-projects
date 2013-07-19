package edu.uwo.csd.dcsim.vm;

import edu.uwo.csd.dcsim.application.*;
import edu.uwo.csd.dcsim.core.Simulation;

/**
 * Describes the general characteristics of a VM, and can instantiate new
 * instances of VMs based on the description
 * 
 * @author Michael Tighe
 *
 */
public class VMDescription {

	private int cores;
	private int coreCapacity;
	private int memory;	
	private int bandwidth;
	private long storage;
	private Task task;
	
	public VMDescription(Task task) {
		this.cores = task.getResourceSize().getCores();
		this.coreCapacity = task.getResourceSize().getCoreCapacity();
		this.memory = task.getResourceSize().getMemory();
		this.bandwidth = task.getResourceSize().getBandwidth();
		this.storage = task.getResourceSize().getStorage();
		this.task = task;
	}
	
	public VMDescription(int cores, int coreCapacity, int memory, int bandwidth, long storage, Task task) {
		this.cores = cores;
		this.coreCapacity = coreCapacity;
		this.memory = memory;
		this.bandwidth = bandwidth;
		this.storage = storage;
		this.task = task;
	}
	
	public VM createVM(Simulation simulation) {
		return new VM(simulation, this, task.createInstance());
	}
	
	public int getCpu() {
		return cores * coreCapacity;
	}
	
	public int getCores() {
		return cores;
	}
	
	public int getCoreCapacity() {
		return coreCapacity;
	}
	
	public int getMemory() {
		return memory;
	}
	
	public double getBandwidth() {
		return bandwidth;
	}
	
	public long getStorage() {
		return storage;
	}
	
	public Task getTask() {
		return task;
	}
	
}
