package edu.uwo.csd.dcsim.projects.distributed.capabilities;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.core.SimulationEventBroadcastGroup;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.capabilities.HostManager;
import edu.uwo.csd.dcsim.projects.distributed.Eviction;
import edu.uwo.csd.dcsim.projects.distributed.events.ResourceOfferEvent;

public class HostManagerBroadcast extends HostManager {

	public enum ManagementState {NORMAL, EVICTING, OFFERING;}
		
	private SimulationEventBroadcastGroup broadcastingGroup; 
	
	private ArrayList<HostStatus> history = new ArrayList<HostStatus>();
	private ArrayList<Host> poweredOffHosts = new ArrayList<Host>();
	private boolean powerStateListValid = true;
	
	//Host state
	private ManagementState managementState = ManagementState.NORMAL;
	private boolean shuttingDown = false;

	//action freezing
	private boolean offerFreeze = false;
	private long offerFreezeExpiry = -1;
	private boolean evictionFreeze = false;
	private long evictionFreezeExpiry = -1;
	private boolean shutdownFreeze = false;
	private long shutdownFreezeExpiry = -1;
	
	//eviction
	private Eviction eviction;
	
	//offer
	private ResourceOfferEvent currentOffer;
	
	
	public HostManagerBroadcast(Host host, SimulationEventBroadcastGroup broadcastingGroup) {
		super(host);
		
		this.broadcastingGroup = broadcastingGroup;
	}
	
	public void addHistoryStatus(HostStatus status, int windowSize) {
		history.add(0, status);
		while (history.size() > windowSize) {
			history.remove(history.size() - 1);
		}
	}
	
	public SimulationEventBroadcastGroup getBroadcastingGroup() {
		return broadcastingGroup;
	}
	
	public ManagementState getManagementState() {
		return managementState;
	}
	
	public void setManagementState(ManagementState state) {
		this.managementState = state;
	}
	
	public ArrayList<HostStatus> getHistory() {
		return history;
	}
	
	
	public ArrayList<Host> getPoweredOffHosts() {
		return poweredOffHosts;
	}

	public Eviction getEviction() {
		return eviction;
	}
	
	public void setEviction(Eviction eviction) {
		this.eviction = eviction;
	}
	
	public void clearEviction() {
		this.eviction = null;
	}
	
	public ResourceOfferEvent getCurrentOffer() {
		return currentOffer;
	}
	
	public void setCurrentOffer(ResourceOfferEvent currentOffer) {
		this.currentOffer = currentOffer;
	}
	
	public void clearCurrentOffer() {
		this.currentOffer = null;
	}
	
	public long getGroupSize() {
		return broadcastingGroup.size();	}
	
	
	public boolean isShuttingDown() {
		return shuttingDown;
	}
	
	public void setShuttingDown(boolean shuttingDown) {
		this.shuttingDown = shuttingDown;
	}
	
	public boolean offersFrozen() {
		return offerFreeze;
	}
	
	public long getOfferFreezeExpiry() {
		return offerFreezeExpiry;
	}
	
	public void enactOfferFreeze(long offerFreezeExpiry) {
		this.offerFreeze = true;
		this.offerFreezeExpiry = offerFreezeExpiry;
	}
	
	public void expireOfferFreeze() {
		this.offerFreeze = false;
	}
	
	public boolean evictionFrozen() {
		return evictionFreeze;
	}
	
	public long getEvictionFreezeExpiry() {
		return evictionFreezeExpiry;
	}
	
	public void enactEvictionFreeze(long evictionFreezeExpiry) {
		this.evictionFreeze = true;
		this.evictionFreezeExpiry = evictionFreezeExpiry;
	}
	
	public void expireEvictionFreeze() {
		this.evictionFreeze = false;
	}
	
	public boolean shutdownFrozen() {
		return shutdownFreeze;
	}
	
	public long getShutdownFreezeExpiry() {
		return shutdownFreezeExpiry;
	}
	
	public void enactShutdownFreeze(long shutdownFreezeExpiry) {
		this.shutdownFreeze = true;
		this.shutdownFreezeExpiry = shutdownFreezeExpiry;
	}
	
	public void expireShutdownFreeze() {
		this.shutdownFreeze = false;
	}
	
	public boolean isPowerStateListValid() {
		return powerStateListValid;
	}
	
	public void setPowerStateListValid(boolean powerStateListValid) {
		this.powerStateListValid = powerStateListValid;
	}
	

}
