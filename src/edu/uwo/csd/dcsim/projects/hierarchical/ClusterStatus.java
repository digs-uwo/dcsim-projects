package edu.uwo.csd.dcsim.projects.hierarchical;

import edu.uwo.csd.dcsim.host.Cluster;
import edu.uwo.csd.dcsim.host.Cluster.ClusterState;
import edu.uwo.csd.dcsim.host.Rack;
import edu.uwo.csd.dcsim.host.Switch;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.RackPoolManager;

public class ClusterStatus {

	private long timeStamp;
	private int id;
	private ClusterState state = ClusterState.OFF;
	private RackStatusVector statusVector = null;		// Spare capacity available in the least loaded Rack (i.e., least active Hosts) in the Cluster.
	private int activeRacks = 0;						// Number of active Racks.
	private double powerConsumption = 0;				// Sum of power consumption from all Racks and Switches in the Cluster.
	
	/**
	 * Creates an *empty* ClusterStatus instance. It only includes time stamp and ID.
	 */
	public ClusterStatus(Cluster cluster, long timeStamp) {
		this.timeStamp = timeStamp;
		id = cluster.getId();
	}
	
	/**
	 * Creates a *complete* ClusterStatus instance.
	 */
	public ClusterStatus(Cluster cluster, RackPoolManager capability, long timeStamp) {
		this.timeStamp = timeStamp;
		id = cluster.getId();
		state = cluster.getState();
		
		if (ClusterState.OFF != state) {
			
			int minActiveHosts = Integer.MAX_VALUE;
			for (RackData rack : capability.getRacks()) {
				
				// Calculate number of active Racks.
				RackStatus status = rack.getCurrentStatus();
				if (status.getState() == Rack.RackState.ON) {
					activeRacks++;
					
					// Check Rack status. If invalid, we cannot make any assertions.
					if (rack.isStatusValid()) {
						
						// Find the least loaded Rack (i.e., the Rack with the least number
						// of active Hosts) and obtain its status vector.
						if (status.getActiveHosts() < minActiveHosts) {
							minActiveHosts = status.getActiveHosts();
							statusVector = status.getStatusVector().copy();
						}
					}
				}
				
				// Calculate Cluster's total power consumption.
				// Note: Even Racks with an invalid status are accounted for here, given that
				// skipping them would represent a good chunk of missing info.
				powerConsumption += rack.getCurrentStatus().getPowerConsumption();
			}
			
			// Add power consumption of the Cluster's Switches.
			powerConsumption += cluster.getMainDataSwitch().getPowerConsumption();
			powerConsumption += cluster.getMainMgmtSwitch().getPowerConsumption();
			if (cluster.getSwitchCount() > 1) {		// Star topology.
				for (Switch s : cluster.getDataSwitches())
					powerConsumption += s.getPowerConsumption();
				for (Switch s : cluster.getMgmtSwitches())
					powerConsumption += s.getPowerConsumption();
			}
		}
	}
	
	public ClusterStatus(ClusterStatus status) {
		timeStamp = status.timeStamp;
		id = status.id;
		state = status.state;
		statusVector = status.getStatusVector() != null ? status.getStatusVector().copy() : null;
		activeRacks = status.activeRacks;
		powerConsumption = status.powerConsumption;
	}
	
	public ClusterStatus copy() {
		return new ClusterStatus(this);
	}

	public long getTimeStamp() {
		return timeStamp;
	}
	
	public int getId() {
		return id;
	}
	
	public ClusterState getState() {
		return state;
	}
	
	public RackStatusVector getStatusVector() {
		return statusVector;
	}
	
	public int getActiveRacks() {
		return activeRacks;
	}
	
	public double getPowerConsumption() {
		return powerConsumption;
	}
	
	/**
	 * Returns the value of the *minInactiveHosts* metric.
	 *
	 * @deprecated always returns zero (0) now!  
	 */
	@Deprecated
	public int getMinInactiveHosts() {
		return 0;
	}
	
	@Deprecated
	public double getMaxSpareCapacity() {
		
		if (null != statusVector)
			for (int i = statusVector.vmVector.length - 1; i >= 0; i--) {
				if (statusVector.vector[i] > 0)
					return StandardVmSizes.calculateSpareCapacity(statusVector.vmVector[i]);
			}
		
		return 0.0;
	}

}
