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
	
	TaskInstance application;
	
	protected Resources resourcesScheduled = new Resources();
	
	public VM(Simulation simulation, VMDescription vmDescription, TaskInstance application) {
		this.simulation = simulation;
		this.id = simulation.nextId(VM.class.toString());
		this.vmDescription = vmDescription;
		this.application = application;
		application.setVM(this);

		vmAllocation = null;
	}
	
	public void updateResourceRequirements() {
		application.updateResourceDemand();
	}
	
	public Resources getResourcesRequired() {
		Resources required = application.getResourceDemand();
		
		//cap CPU request at max CPU
		required.setCpu(Math.min(required.getCpu(), getMaxCpu()));
		
		return required;
	}
	
	public Resources getResourcesScheduled() {
		
		//TODO return a copy? make VirtualResources immutable?
		
		return resourcesScheduled;
	}
	
	public void scheduleResources(Resources resources) {
		
		//double check that we are not trying to schedule more than maxCpu
		if (resources.getCpu() > getMaxCpu()) {
			throw new RuntimeException("Attempted to schedule a VM more CPU than is possible (> maxCpu)");
		}
		
		this.resourcesScheduled = resources;
		application.scheduleResources(resources);
	}
	
	public void postScheduling() {
		application.postScheduling();
	}
	
	public int getMaxCpu() {
		return vmDescription.getCores() * vmAllocation.getHost().getCoreCapacity();
	}
	
	public void startApplication() {
		vmDescription.getApplicationFactory().startInstance(application);
	}
	
	public void stopApplication() {
		vmDescription.getApplicationFactory().removeInstance(application);
	}
	
	public void updateMetrics() {
		
		VmCountMetric.getMetric(simulation, VM_COUNT_METRIC).incrementVmCount();
		
		application.updateMetrics();
	}
	
	public void logState() {
		if (getVMAllocation().getHost().getState() == Host.HostState.ON) {
			simulation.getLogger().debug("VM #" + getId() + " CPU[" + Utility.roundDouble(resourcesScheduled.getCpu(), 2) + 
					"/" + vmAllocation.getCpu() + 
					"/" + Utility.roundDouble(application.getResourceDemand().getCpu(), 2) + "] " + 
					"BW[" + Utility.roundDouble(resourcesScheduled.getBandwidth(), 2) + 
					"/" + vmAllocation.getBandwidth() + 
					"/" + Utility.roundDouble(application.getResourceDemand().getBandwidth(), 2) + "] " + 
					"MEM[" + resourcesScheduled.getMemory() + 
					"/" + vmAllocation.getMemory() + "] " +
					"STORAGE[" + resourcesScheduled.getStorage() + 
					"/" + vmAllocation.getStorage() + "]");
		}
		
		//trace output
		simulation.getTraceLogger().info("#v," + getId() + "," + vmAllocation.getHost().getId() + "," + 
				Utility.roundDouble(resourcesScheduled.getCpu(), 2) + "," + Utility.roundDouble(application.getResourceDemand().getCpu(), 2) + "," + 
				Utility.roundDouble(resourcesScheduled.getBandwidth(), 2) + "," + Utility.roundDouble(application.getResourceDemand().getBandwidth(), 2) + "," + 
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
	
	public TaskInstance getApplication() {
		return application;
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
