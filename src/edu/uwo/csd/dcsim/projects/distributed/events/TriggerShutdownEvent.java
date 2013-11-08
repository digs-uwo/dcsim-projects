package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class TriggerShutdownEvent extends MessageEvent {

	private AutonomicManager coordinator;
	private Host coordinatorHost;
	private Resources resources;
	private int vmCount;
	
	public TriggerShutdownEvent(SimulationEventListener target, AutonomicManager coordinator, Host coordinatorHost, Resources resources, int vmCount) {
		super(target);
		
		this.coordinator = coordinator;
		this.resources = resources;
		this.vmCount = vmCount;
		this.coordinatorHost = coordinatorHost;
	}
	
	public AutonomicManager getCoordinator() {
		return coordinator;
	}
	
	public Resources getResources() {
		return resources;
	}
	
	public int getVmCount() {
		return vmCount;
	}
	
	public Host getCoordinatorHost() {
		return coordinatorHost;
	}
	
}
