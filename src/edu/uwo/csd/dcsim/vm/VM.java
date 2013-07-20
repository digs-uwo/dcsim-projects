package edu.uwo.csd.dcsim.vm;

import edu.uwo.csd.dcsim.application.*;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.core.*;
import edu.uwo.csd.dcsim.core.metrics.VmCountMetric;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;

/**
 * Represents a Virtual Machine, running a Task Instance. Must be contained within a VMAllocation on a Host
 * 
 * @author Michael Tighe
 *
 */
public class VM implements SimulationEventListener {

	private static final String VM_COUNT_METRIC = "vmCount";
	
	Simulation simulation;
	int id;
	VMDescription vmDescription;
	VMAllocation vmAllocation; //the allocation this VM is running within
	
	TaskInstance taskInstance;
	
	protected Resources resourcesScheduled = new Resources();
	
	public VM(Simulation simulation, VMDescription vmDescription, TaskInstance taskInstance) {
		this.simulation = simulation;
		this.id = simulation.nextId(VM.class.toString());
		this.vmDescription = vmDescription;
		this.taskInstance = taskInstance;
		taskInstance.setVM(this);

		vmAllocation = null;
	}
	
	public Resources getResourceDemand() {
		
		//make a copy to prevent it from being modified
		Resources demand = new Resources(taskInstance.getResourceDemand());
		
		//cap CPU request at max CPU
		demand.setCpu(Math.min(demand.getCpu(), getMaxCpu()));
		
		return demand;
	}
	
	public Resources getResourcesScheduled() {
		
		//prevent editing of this value outside of scheduleResources(), since the taskInstance value must be the same
		
		return new Resources(resourcesScheduled);
	}
	
	public void scheduleResources(Resources resources) {
		
		//double check that we are not trying to schedule more than maxCpu
		if (resources.getCpu() > getMaxCpu()) {
			throw new RuntimeException("Attempted to schedule a VM more CPU than is possible (> maxCpu)");
		}
		
		this.resourcesScheduled = resources;
		taskInstance.setResourceScheduled(resources);
	}
	
	public int getMaxCpu() {
		return vmDescription.getCores() * vmAllocation.getHost().getCoreCapacity();
	}
	
	public void startTaskInstance() {
		vmDescription.getTask().startInstance(taskInstance);
	}
	
	public void stopTaskInstance() {
		vmDescription.getTask().stopInstance(taskInstance);
	}
	
	public void updateMetrics() {
		
		VmCountMetric.getMetric(simulation, VM_COUNT_METRIC).incrementVmCount();
		
		//application.updateMetrics();
	}
	
	public void logState() {
		if (getVMAllocation().getHost().getState() == Host.HostState.ON) {
			simulation.getLogger().debug("VM #" + getId() + " CPU[" + Utility.roundDouble(resourcesScheduled.getCpu(), 2) + 
					"/" + vmAllocation.getCpu() + 
					"/" + Utility.roundDouble(getResourceDemand().getCpu(), 2) + "] " + 
					"BW[" + Utility.roundDouble(resourcesScheduled.getBandwidth(), 2) + 
					"/" + vmAllocation.getBandwidth() + 
					"/" + Utility.roundDouble(getResourceDemand().getBandwidth(), 2) + "] " + 
					"MEM[" + resourcesScheduled.getMemory() + 
					"/" + vmAllocation.getMemory() + "] " +
					"STORAGE[" + resourcesScheduled.getStorage() + 
					"/" + vmAllocation.getStorage() + "]");
		}
		
		//trace output
		simulation.getTraceLogger().info("#v," + getId() + "," + vmAllocation.getHost().getId() + "," + 
				Utility.roundDouble(resourcesScheduled.getCpu(), 2) + "," + Utility.roundDouble(getResourceDemand().getCpu(), 2) + "," + 
				Utility.roundDouble(resourcesScheduled.getBandwidth(), 2) + "," + Utility.roundDouble(getResourceDemand().getBandwidth(), 2) + "," + 
				resourcesScheduled.getMemory() + "," + vmAllocation.getMemory() + "," +
				resourcesScheduled.getStorage() + "," + vmAllocation.getStorage());
		
	}
	
	public boolean isMigrating() {
		return vmAllocation.getHost().isMigrating(this);
	}
	
	public boolean isPendingMigration() {
		return vmAllocation.getHost().isPendingMigration(this);
	}
	
	public int getId() {
		return id;
	}
	
	public TaskInstance getTaskInstance() {
		return taskInstance;
	}
	
	public VMDescription getVMDescription() {
		return vmDescription;
	}
	
	public VMAllocation getVMAllocation() {
		return vmAllocation;
	}
	
	public void setVMAllocation(VMAllocation vmAllocation) {
		this.vmAllocation = vmAllocation;
	}

	@Override
	public void handleEvent(Event e) {
		// TODO Auto-generated method stub
		
	}


}
