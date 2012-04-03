package edu.uwo.csd.dcsim2.vm;

public class VMAllocationRequest {

	private VMDescription vmDescription;
	private int cpu;
	private int memory;
	private int bandwidth;
	private long storage;

	public VMAllocationRequest(VMDescription vmDescription,
			int cpu, 
			int memory, 
			int bandwidth, 
			long storage) {
		
		this.vmDescription = vmDescription;
		this.cpu = cpu;
		this.memory = memory;
		this.bandwidth = bandwidth;
		this.storage = storage;
	}
	
	public VMAllocationRequest(VMAllocation vmAllocation) {
		vmDescription = vmAllocation.getVMDescription();
		
		cpu = vmAllocation.getCpu();
		memory = vmAllocation.getMemory();
		bandwidth = vmAllocation.getBandwidth();
		storage = vmAllocation.getStorage();	
	}
	
	public VMAllocationRequest(VMDescription vmDescription) {
		this.vmDescription =  vmDescription;
		
		cpu = vmDescription.getCores() * vmDescription.getCoreCapacity();
		memory = vmDescription.getMemory();
		bandwidth = vmDescription.getBandwidth();
		storage = vmDescription.getStorage();
	}
	
	public VMDescription getVMDescription() {
		return vmDescription;
	}

	public int getCpu() {
		return cpu;
	}
	
	public int getMemory() {
		return memory;
	}
	
	public int getBandwidth() {
		return bandwidth;
	}
	
	public long getStorage() {
		return storage;
	}
	
}
