package edu.uwo.csd.dcsim.projects.distributed.capabilities;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.core.SimulationEventBroadcastGroup;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.capabilities.HostManager;
import edu.uwo.csd.dcsim.projects.distributed.Eviction;
import edu.uwo.csd.dcsim.projects.distributed.events.ResourceOfferEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.ShutdownClaimEvent;

public class HostManagerBroadcast extends HostManager {

	public enum ManagementState {NORMAL, EVICTING, OFFERING;}
	public enum ShutdownState {NONE, SHUTTING_DOWN, SUBMITTED_CLAIM, COORDINATING;}
		
	private SimulationEventBroadcastGroup broadcastingGroup; 
	
	private ArrayList<HostStatus> history = new ArrayList<HostStatus>();
	private ArrayList<Host> poweredOffHosts = new ArrayList<Host>();
	private boolean powerStateListValid = true;
	
	//Host state
	private ManagementState managementState = ManagementState.NORMAL;
	private ShutdownState shutdownState = ShutdownState.NONE;
	
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
	
	//shutdown
	private ArrayList<ShutdownClaimEvent> shutdownClaims = new ArrayList<ShutdownClaimEvent>();
	private boolean shutdownResourcesAvailable = false;
	
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
	
	public ArrayList<ShutdownClaimEvent> getShutdownClaims() {
		return shutdownClaims;
	}
	
	public ManagementState getManagementState() {
		return managementState;
	}
	
	public void setManagementState(ManagementState state) {
		this.managementState = state;
	}
	
	public ShutdownState getShutdownState() {
		return shutdownState;
	}
	
	public void setShutdownState(ShutdownState state) {
		shutdownState = state;
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
		return broadcastingGroup.size();	
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

	public boolean areShutdownResourcesvailable() {
		return shutdownResourcesAvailable;
	}
	
	public void setShutdownResourcesAvailable(boolean shutdownResourcesAvailable) {
		this.shutdownResourcesAvailable = shutdownResourcesAvailable;
	}

}
