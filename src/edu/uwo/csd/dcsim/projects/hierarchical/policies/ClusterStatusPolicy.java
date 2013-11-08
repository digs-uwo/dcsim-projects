package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.management.Policy;
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
		
		capability.getCluster(event.getClusterStatus().getId()).addClusterStatus(event.getClusterStatus(), windowSize);
	}

	@Override
	public void onInstall() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStart() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStop() {
		// TODO Auto-generated method stub
		
	}

}
