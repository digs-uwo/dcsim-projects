package edu.uwo.csd.dcsim.projects.hierarchical.capabilities;

import java.util.*;

import edu.uwo.csd.dcsim.host.Cluster;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.capabilities.HostCapability;
import edu.uwo.csd.dcsim.projects.hierarchical.ClusterData;

public class ClusterPoolManager extends HostCapability {
	
	private Map<Integer, ClusterData> clusterMap = new HashMap<Integer, ClusterData>();
	
	public void addCluster(Cluster cluster, AutonomicManager clusterManager) {
		clusterMap.put(cluster.getId(), new ClusterData(cluster, clusterManager));
	}
	
	public Collection<ClusterData> getClusters() {
		return clusterMap.values();
	}
	
	public ClusterData getCluster(int id) {
		return clusterMap.get(id);
	}
}
