package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.events.MessageEvent;
import edu.uwo.csd.dcsim.projects.distributed.Eviction;

public class RequestResourcesEvent extends MessageEvent {

	public enum AdvertiseReason {STRESS, SHUTDOWN, PLACEMENT;}
	
	private Eviction eviction;
	private Resources minResources;
	private AutonomicManager hostManager;
	private AdvertiseReason reason;
	
	public RequestResourcesEvent(SimulationEventListener target, Resources minResources, Eviction eviction, AutonomicManager hostManager, AdvertiseReason reason) {
		super(target);
		
		this.minResources = minResources;
		this.eviction = eviction;
		this.hostManager = hostManager;
		this.reason = reason;
	}
	
	public Resources getMinResources() {
		return minResources;
	}
	
	public AutonomicManager getHostManager() {
		return hostManager;
	}
	
	public AdvertiseReason getReason() {
		return reason;
	}

	public Eviction getEviction() {
		return eviction;
	}
	
}
