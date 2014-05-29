package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.projects.hierarchical.ClusterStatus;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.ClusterPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.events.ClusterStatusEvent;

public class ClusterStatusPolicy extends Policy {

	ArrayList<Class<? extends Event>> triggerEvents = new ArrayList<Class<? extends Event>>();
	
	private int windowSize;
	
	public ClusterStatusPolicy(int windowSize) {
		addRequiredCapability(ClusterPoolManager.class);
		
		this.windowSize = windowSize;
	}

	public void execute(ClusterStatusEvent event) {		
		ClusterPoolManager capability = manager.getCapability(ClusterPoolManager.class);
		
		ClusterStatus status = event.getClusterStatus();
		
		simulation.getLogger().debug(String.format("Status update for Cluster #%d.", status.getId()));
		
		capability.getCluster(status.getId()).addClusterStatus(status, windowSize);
	}

	@Override
	public void onInstall() {
		// Auto-generated method stub
	}

	@Override
	public void onManagerStart() {
		// Auto-generated method stub
	}

	@Override
	public void onManagerStop() {
		// Auto-generated method stub
	}

}
