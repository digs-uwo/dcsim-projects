package edu.uwo.csd.dcsim.projects.hierarchical;

import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.host.Rack.RackState;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;

public class RackStatus {

	private long timeStamp;
	private int id;
	
	private Rack.RackState state = Rack.RackState.OFF;
	
	private final Resources[] vmVector = {VmFlavours.xtiny(), VmFlavours.tiny(), VmFlavours.small(), VmFlavours.medium(), VmFlavours.large(), VmFlavours.xlarge()};
	
	private int[] spareCapacityVector = new int[3 + vmVector.length];	// [active, suspended, poweredOff] + vmVector
	private final int iActive = 0;
	private final int iSuspended = 1;
	private final int iPoweredOff = 2;
	private final int iVmVector = 3;
	
	private double powerConsumption = 0;		// Sum of power consumption from all Hosts and Switches in the Rack.
	
	/**
	 * Creates an *empty* RackStatus instance.
	 */
	public RackStatus(Rack rack, long timeStamp) {
		this.timeStamp = timeStamp;
		id = rack.getId();
		spareCapacityVector[iPoweredOff] = rack.getHostCount();
	}
	
	/**
	 * Creates a *complete* RackStatus instance.
	 */
	public RackStatus(Rack rack, HostPoolManager capability, long timeStamp) {
		this.timeStamp = timeStamp;
		id = rack.getId();
		state = rack.getState();
		
		if (state == RackState.OFF) {
			spareCapacityVector[iPoweredOff] = rack.getHostCount();
			powerConsumption = 0;
		}
		else {
			for (HostData host : capability.getHosts()) {
				
				// Calculate number of active, suspended and powered-off Hosts.
				Host.HostState state = host.getCurrentStatus().getState();
				if (state == Host.HostState.ON || state == Host.HostState.POWERING_ON) {
					spareCapacityVector[iActive]++;
					
					// Calculate spare capacity for each active Host.
					// Check Host status. If invalid, we cannot make any assertions.
					if (host.isStatusValid()) {
						for (int i = vmVector.length - 1; i >= 0; i--) {
							
							// TODO: canHost() checks space up to full Host capacity, being completely unaware of any target or stress thresholds. THIS IS A PROBLEM.
							
							if (HostData.canHost(vmVector[i].getCores(), vmVector[i].getCoreCapacity(), vmVector[i], host.getCurrentStatus(), host.getHostDescription())) {
								spareCapacityVector[iVmVector + i]++;
								break;
							}
						}
					}
					
				}
				else if (state == Host.HostState.SUSPENDED || state == Host.HostState.SUSPENDING)
					spareCapacityVector[iSuspended]++;
				else if (state == Host.HostState.OFF || state == Host.HostState.POWERING_OFF)
					spareCapacityVector[iPoweredOff]++;
				
				// Calculate Rack's total power consumption.
				// Note: Even Hosts with an invalid status are accounted for here, given that
				// skipping them would represent a good chunk of missing info.
				powerConsumption += host.getCurrentStatus().getPowerConsumption();
			}
			
			// Add power consumption of the Rack's switches.
			powerConsumption += rack.getDataNetworkSwitch().getPowerConsumption();
			powerConsumption += rack.getMgmtNetworkSwitch().getPowerConsumption();
		}
	}
	
	public RackStatus(RackStatus status) {
		timeStamp = status.timeStamp;
		id = status.id;
		state = status.state;
		spareCapacityVector = status.spareCapacityVector;
		powerConsumption = status.powerConsumption;
	}
	
	public RackStatus copy() {
		return new RackStatus(this);
	}

	public long getTimeStamp() {
		return timeStamp;
	}
	
	public int getId() {
		return id;
	}
	
	public Rack.RackState getState() {
		return state;
	}
	
	public Resources[] getVmVector() {
		return vmVector;
	}
	
	public int[] getSpareCapacityVector() {
		return spareCapacityVector;
	}
	
	public int getActiveHosts() {
		return spareCapacityVector[iActive];
	}
	
	public int getActiveHostsIndex() {
		return iActive;
	}
	
	public int getSuspendedHosts() {
		return spareCapacityVector[iSuspended];
	}
	
	public int getSuspendedHostsIndex() {
		return iSuspended;
	}
	
	public int getPoweredOffHosts() {
		return spareCapacityVector[iPoweredOff];
	}
	
	public int getPoweredOffHostsIndex() {
		return iPoweredOff;
	}
	
	public double getPowerConsumption() {
		return powerConsumption;
	}
	
	@Deprecated
	public double getMaxSpareCapacity() {
		
		for (int i = vmVector.length - 1; i >= 0; i--) {
			if (spareCapacityVector[i] > 0)
				return StandardVmSizes.calculateSpareCapacity(vmVector[i]);
		}
		
		return 0.0;
	}

}
