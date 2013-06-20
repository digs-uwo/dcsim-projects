package edu.uwo.csd.dcsim.projects.hierarchical;

import edu.uwo.csd.dcsim.host.*;

public class RackStatus {

	private long timeStamp;
	private int id;
	
	private int activeHosts = 0;
	private int suspendedHosts = 0;
	private int poweredOffHosts = 0;
	
	// max spare capacity: list of vectors or single vector  ( Resources object ? )
	
	private double powerConsumption = 0;		// Sum of power consumption from all Hosts and Switches in the Rack.
	
	public RackStatus(Rack rack, long timeStamp) {
		this.timeStamp = timeStamp;
		
		id = rack.getId();
		
		for (Host host : rack.getHosts()) {
			// Calculate number of Active, Suspended and Powered-Off hosts.
			if (host.getState() == Host.HostState.ON || host.getState() == Host.HostState.POWERING_ON)
				activeHosts++;
			else if (host.getState() == Host.HostState.SUSPENDED || host.getState() == Host.HostState.SUSPENDING)
				suspendedHosts++;
			else if (host.getState() == Host.HostState.OFF || host.getState() == Host.HostState.POWERING_OFF)
				poweredOffHosts++;
			
			// max spare capacity ??
			
			// Calculate Rack's total power consumption.
			powerConsumption += host.getCurrentPowerConsumption();
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
