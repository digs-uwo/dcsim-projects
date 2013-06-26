package edu.uwo.csd.dcsim.projects.hierarchical;

import edu.uwo.csd.dcsim.host.Cluster;

public class ClusterDescription {

	private int rackCount;
	private RackDescription rackDescription;			// Number of hosts per rack, plus static resource capacity of a single Host.
	
	// power efficiency ???
	
	public ClusterDescription(Cluster cluster) {
		rackCount = cluster.getRackCount();
		rackDescription = new RackDescription(cluster.getRacks().get(0));
	}
	
	public int getRackCount() {
		return rackCount;
	}
	
	public RackDescription getRackDescription() {
		return rackDescription;
	}

}
