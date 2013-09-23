package edu.uwo.csd.dcsim.projects.hierarchical;

import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;

public class RackStatus {

	private long timeStamp;
	private int id;
	
	private Rack.RackState state = Rack.RackState.OFF;
	
	private int activeHosts = 0;
	private int suspendedHosts = 0;
	private int poweredOffHosts = 0;
	private double maxSpareCapacity = 0;		// Amount of spare resources available in the least loaded active Host in the Rack.
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
			
			// Calculate number of active, suspended and powered-off Hosts.
			Host.HostState state = host.getCurrentStatus().getState();
			if (state == Host.HostState.ON || state == Host.HostState.POWERING_ON) {
				activeHosts++;
				
				// Calculate spare capacity for each active Host.
				// Check Host status. If invalid, we cannot make any assertions.
				if (host.isStatusValid()) {
					double capacity = StandardVmSizes.calculateSpareCapacity(host);
					if (capacity > maxSpareCapacity)
						maxSpareCapacity = capacity;
				}
				
			}
			else if (state == Host.HostState.SUSPENDED || state == Host.HostState.SUSPENDING)
				suspendedHosts++;
			else if (state == Host.HostState.OFF || state == Host.HostState.POWERING_OFF)
				poweredOffHosts++;
			
			// Calculate Rack's total power consumption.
			// Note: Even Hosts with an invalid status are accounted for here, given that
			// skipping them would represent a good chunk of missing info.
			powerConsumption += host.getCurrentStatus().getPowerConsumption();
		}
		
		// Add power consumption of the Rack's switches.
		powerConsumption += rack.getDataNetworkSwitch().getPowerConsumption();
		powerConsumption += rack.getMgmtNetworkSwitch().getPowerConsumption();
		
		// Determine whether the Rack is active (i.e., ON) or inactive (i.e., SUSPENDED or OFF).
		if (activeHosts > 0)
			state = Rack.RackState.ON;
		else if (suspendedHosts > 0)
			state = Rack.RackState.SUSPENDED;
		else
			state = Rack.RackState.OFF;
	}
	
	public RackStatus(RackStatus status) {
		timeStamp = status.timeStamp;
		id = status.id;
		state = status.state;
		activeHosts = status.activeHosts;
		suspendedHosts = status.suspendedHosts;
		poweredOffHosts = status.poweredOffHosts;
		maxSpareCapacity = status.maxSpareCapacity;
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
	
	public int getActiveHosts() {
		return activeHosts;
	}
	
	public int getSuspendedHosts() {
		return suspendedHosts;
	}
	
	public int getPoweredOffHosts() {
		return poweredOffHosts;
	}
	
	public double getMaxSpareCapacity() {
		return maxSpareCapacity;
	}
	
	public double getPowerConsumption() {
		return powerConsumption;
	}

}
