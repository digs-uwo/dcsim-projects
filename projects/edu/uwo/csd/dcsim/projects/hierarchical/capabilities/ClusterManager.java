package edu.uwo.csd.dcsim.projects.hierarchical.capabilities;

import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.management.capabilities.HostCapability;

public class ClusterManager extends HostCapability {

	private Cluster cluster;
	
	public ClusterManager(Cluster cluster) {
		this.cluster = cluster;
	}
	
	public Cluster getCluster() {
		return cluster;
	}

}
