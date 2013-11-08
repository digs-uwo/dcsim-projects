package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.RackStatus;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.RackManager;
import edu.uwo.csd.dcsim.projects.hierarchical.events.RackStatusEvent;

public class RackMonitoringPolicy extends Policy {

	SimulationEventListener target;
	
	public RackMonitoringPolicy(SimulationEventListener target) {
		addRequiredCapability(RackManager.class);
		addRequiredCapability(HostPoolManager.class);
		
		this.target = target;
	}

	@Override
	public void onManagerStop() {
		//execute the monitor so that a final message is sent indicating that the host is now OFF
		execute();
	}
	
	public void execute() {
		RackManager rackManager = manager.getCapability(RackManager.class);
		HostPoolManager hostPoolManager = manager.getCapability(HostPoolManager.class);
		
		RackStatus status = new RackStatus(rackManager.getRack(), hostPoolManager, simulation.getSimulationTime());
		
		simulation.sendEvent(new RackStatusEvent(target, status));
		
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
