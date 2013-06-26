package edu.uwo.csd.dcsim.projects.hierarchical;

import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;

public class RackStatus {

	private long timeStamp;
	private int id;
	
	//private Rack.RackState state;
	
	private int activeHosts = 0;
	private int suspendedHosts = 0;
	private int poweredOffHosts = 0;
	
	// max spare capacity: list of vectors or single vector  ( Resources object ? )
	
	private double powerConsumption = 0;		// Sum of power consumption from all Hosts and Switches in the Rack.
	
	/**
	 * Creates an *empty* RackStatus instance. It only includes time stamp and ID.
	 */
	public RackStatus(Rack rack, long timeStamp) {
		this.timeStamp = timeStamp;
		id = rack.getId();
	}
	
	/**
	 * Creates a *complete* RackStatus instance.
	 */
	public RackStatus(Rack rack, HostPoolManager capability, long timeStamp) {
		this.timeStamp = timeStamp;
		id = rack.getId();
		
		for (HostData host : capability.getHosts()) {
			// Calculate number of Active, Suspended and Powered-Off hosts.
			Host.HostState state = host.getCurrentStatus().getState();
			if (state == Host.HostState.ON || state == Host.HostState.POWERING_ON)
				activeHosts++;
			else if (state == Host.HostState.SUSPENDED || state == Host.HostState.SUSPENDING)
				suspendedHosts++;
			else if (state == Host.HostState.OFF || state == Host.HostState.POWERING_OFF)
				poweredOffHosts++;
			
			// max spare capacity ??
			
			// Calculate Rack's total power consumption.
			powerConsumption += host.getCurrentStatus().getPowerConsumption();
		}
		
		// Add power consumption of the Rack's switches.
		powerConsumption += rack.getDataNetworkSwitch().getPowerConsumption();
		powerConsumption += rack.getMgmtNetworkSwitch().getPowerConsumption();
	}
	
	public RackStatus(RackStatus status) {
		timeStamp = status.timeStamp;
		id = status.id;
		
		activeHosts = status.activeHosts;
		suspendedHosts = status.suspendedHosts;
		poweredOffHosts = status.poweredOffHosts;
		
		// max spare capacity
		
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
	
	public int getActiveHosts() {
		return activeHosts;
	}
	
	public int getSuspendedHosts() {
		return suspendedHosts;
	}
	
	public int getPoweredOffHosts() {
		return poweredOffHosts;
	}
	
	public double getPowerConsumption() {
		return powerConsumption;
	}

}
