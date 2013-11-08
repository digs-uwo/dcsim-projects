package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.events.MessageEvent;
import edu.uwo.csd.dcsim.projects.distributed.Eviction;

public class ResourceOfferEvent extends MessageEvent {

	private static long nextId = 1;
	
	private long id;
	private Eviction eviction;
	private Host host;
	private Resources resourcesOffered;
	private AutonomicManager hostManager;
	private HostStatus hostStatus;
	
	public ResourceOfferEvent(SimulationEventListener target, Eviction eviction, Host host, AutonomicManager hostManager, HostStatus hostStatus, Resources resourcesOffered) {
		super(target);
		
		id = nextId++;
		this.eviction = eviction;
		this.host = host;
		this.hostManager = hostManager;
		this.hostStatus = hostStatus.copy();		//make a copy, so we can modify it during target selection
		this.resourcesOffered = resourcesOffered;
	}
	
	public long getOfferId() {
		return id;
	}
	
	public Eviction getEviction() {
		return eviction;
	}
	
	public Host getHost() {
		return host;
	}
	
	public AutonomicManager getHostManager() {
		return hostManager;
	}
	
	public HostStatus getHostStatus() {
		return hostStatus;
	}
	
	public Resources getResourcesOffered() {
		return resourcesOffered;
	}

}
