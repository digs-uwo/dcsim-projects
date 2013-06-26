package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.projects.hierarchical.ClusterStatus;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.*;
import edu.uwo.csd.dcsim.projects.hierarchical.events.ClusterStatusEvent;

public class ClusterMonitoringPolicy extends Policy {

	SimulationEventListener target;
	
	public ClusterMonitoringPolicy(SimulationEventListener target) {
		addRequiredCapability(ClusterManager.class);
		addRequiredCapability(RackPoolManager.class);
		
		this.target = target;
	}

	@Override
	public void onManagerStop() {
		//execute the monitor so that a final message is sent indicating that the host is now OFF
		execute();
	}
	
	public void execute() {
		ClusterManager clusterManager = manager.getCapability(ClusterManager.class);
		RackPoolManager rackPoolManager = manager.getCapability(RackPoolManager.class);
		
		ClusterStatus status = new ClusterStatus(clusterManager.getCluster(), rackPoolManager, simulation.getSimulationTime());
		
		simulation.sendEvent(new ClusterStatusEvent(target, status));
		
		if (simulation.isRecordingMetrics()) {
			
			// TODO Any metrics to record ???
			
		}
	}

	@Override
	public void onInstall() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStart() {
		// TODO Auto-generated method stub
		
	}

}
