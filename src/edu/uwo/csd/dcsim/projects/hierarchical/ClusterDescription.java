package edu.uwo.csd.dcsim.projects.hierarchical;

import edu.uwo.csd.dcsim.host.Cluster;

public class ClusterDescription {

	private int rackCount;								// Number of Racks in the Cluster.
	private RackDescription rackDescription;			// Physical description of a Rack.
	private double powerEfficiency = 0;				// Total CPU units over power consumption when the Cluster is fully utilized.
	
	// TODO Power efficiency only takes into account power consumption of the Hosts; it does not include the power consumption 
	// of the Switches. For that purpose, we'd need HostDescription to provide the power consumption of the Host at full load, 
	// and RackDescription and ClusterDescription to include number of Switches and their power consumption (or description).
	
	public ClusterDescription(Cluster cluster) {
		rackCount = cluster.getRackCount();
		rackDescription = new RackDescription(cluster.getRacks().get(0));
		powerEfficiency = rackDescription.getPowerEfficiency() * rackCount;
	}
	
	public int getRackCount() {
		return rackCount;
	}
	
	public RackDescription getRackDescription() {
		return rackDescription;
	}
	
	public double getPowerEfficiency() {
		return powerEfficiency;
	}

}
