package edu.uwo.csd.dcsim.projects.applicationManagement.capabilities;

import java.util.*;

import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;

public class DataCentreManager extends HostPoolManager {

	protected Map<Cluster, Collection<Rack>> clusters = new HashMap<Cluster, Collection<Rack>>();
	protected Map<Rack, Collection<HostData>> racks = new HashMap<Rack, Collection<HostData>>();

	@Override
	public void addHost(Host host, AutonomicManager hostManager) {
		HostData hostData = new HostData(host, hostManager);
		hostMap.put(host.getId(), hostData);
		
		Rack rack = host.getRack();
		if (rack != null) {
			if (!racks.containsKey(rack)) racks.put(rack, new ArrayList<HostData>());
			racks.get(rack).add(hostData);
			
			Cluster cluster = rack.getCluster();
			if (cluster != null) {
				if (!clusters.containsKey(cluster)) clusters.put(cluster, new ArrayList<Rack>());
				Collection<Rack> clusterRacks = clusters.get(cluster);
				if (!clusterRacks.contains(rack)) clusterRacks.add(rack);
			}
		}
	}
	
	public Collection<HostData> getHosts(Rack rack) {
		return racks.get(rack);
	}
	
	public Collection<HostData> getHosts(Cluster cluster) {
		ArrayList<HostData> hostData = new ArrayList<HostData>();
		
		if (clusters.containsKey(cluster)) {
			for (Rack rack : clusters.get(cluster)) {
				hostData.addAll(getHosts(rack));
			}
		}

		return hostData;
	}
	
}
