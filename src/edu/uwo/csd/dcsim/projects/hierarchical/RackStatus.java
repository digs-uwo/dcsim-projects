package edu.uwo.csd.dcsim.projects.hierarchical;

import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.host.Rack.RackState;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;

public class RackStatus {

	private long timeStamp;
	private int id;
	private Rack.RackState state = Rack.RackState.OFF;
	private RackStatusVector statusVector = new RackStatusVector();
	private double powerConsumption = 0;		// Sum of power consumption from all Hosts and Switches in the Rack.
	
	/**
	 * Creates an *empty* RackStatus instance.
	 */
	public RackStatus(Rack rack, long timeStamp) {
		this.timeStamp = timeStamp;
		id = rack.getId();
		statusVector.vector[statusVector.iPoweredOff] = rack.getHostCount();
	}
	
	/**
	 * Creates a *complete* RackStatus instance.
	 */
	public RackStatus(Rack rack, HostPoolManager capability, long timeStamp) {
		this.timeStamp = timeStamp;
		id = rack.getId();
		state = rack.getState();
		
		if (state == RackState.OFF) {
			statusVector.vector[statusVector.iPoweredOff] = rack.getHostCount();
			powerConsumption = 0;
		}
		else {
			for (HostData host : capability.getHosts()) {
				
				// Calculate number of active, suspended and powered-off Hosts.
				Host.HostState state = host.getCurrentStatus().getState();
				if (state == Host.HostState.ON || state == Host.HostState.POWERING_ON) {
					statusVector.vector[statusVector.iActive]++;
					
					// Calculate spare capacity for each active Host.
					// Check Host status. If invalid, we cannot make any assertions.
					if (host.isStatusValid()) {
						for (int i = statusVector.vmVector.length - 1; i >= 0; i--) {
							
							// TODO: canHost() checks space up to full Host capacity, being completely unaware of any target or stress thresholds. THIS IS A PROBLEM.
							
							if (HostData.canHost(statusVector.vmVector[i].getCores(), statusVector.vmVector[i].getCoreCapacity(), statusVector.vmVector[i], host.getCurrentStatus(), host.getHostDescription())) {
								statusVector.vector[statusVector.iVmVector + i]++;
								break;
							}
						}
					}
					
				}
				else if (state == Host.HostState.SUSPENDED || state == Host.HostState.SUSPENDING)
					statusVector.vector[statusVector.iSuspended]++;
				else if (state == Host.HostState.OFF || state == Host.HostState.POWERING_OFF)
					statusVector.vector[statusVector.iPoweredOff]++;
				
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
		statusVector = new RackStatusVector(status.getStatusVector());
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
	
	public RackStatusVector getStatusVector() {
		return statusVector;
	}
	
	public double getPowerConsumption() {
		return powerConsumption;
	}
	
	@Deprecated
	public int getActiveHosts() {
		return statusVector.vector[statusVector.iActive];
	}
	
	@Deprecated
	public int getSuspendedHosts() {
		return statusVector.vector[statusVector.iSuspended];
	}
	
	@Deprecated
	public int getPoweredOffHosts() {
		return statusVector.vector[statusVector.iPoweredOff];
	}
	
	@Deprecated
	public double getMaxSpareCapacity() {
		
		for (int i = statusVector.vmVector.length - 1; i >= 0; i--) {
			if (statusVector.vector[i] > 0)
				return StandardVmSizes.calculateSpareCapacity(statusVector.vmVector[i]);
		}
		
		return 0.0;
	}

}
