package edu.uwo.csd.dcsim.projects.distributed.capabilities;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.core.SimulationEventBroadcastGroup;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.capabilities.HostManager;
import edu.uwo.csd.dcsim.projects.distributed.events.BidVmEvent;

public class HostManagerBroadcast extends HostManager {

	public enum ManagementState {NORMAL, EVICTING, BIDDING;}
		
	private SimulationEventBroadcastGroup broadcastingGroup; 
	private ArrayList<HostStatus> history = new ArrayList<HostStatus>();
	private int evicting = 0;
	private VmStatus evictingVm;
	
	private ArrayList<Host> poweredOffHosts = new ArrayList<Host>();
	private ArrayList<BidVmEvent> vmAccepts = new ArrayList<BidVmEvent>();
	private long lastShutdownEvent = 0;
	
	//Host state
	private ManagementState managementState = ManagementState.NORMAL;
	private boolean shuttingDown = false;
	
	private boolean bidFreeze = false;
	private long bidFreezeExpiry = -1;
	private boolean evictionFreeze = false;
	private long evictionFreezeExpiry = -1;
	private boolean shutdownFreeze = false;
	private long shutdownFreezeExpiry = -1;
	
	
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
	
	public VmStatus getEvictingVm() {
		return evictingVm;
	}
	
	public void setEvictingVm(VmStatus evictingVm) {
		this.evictingVm = evictingVm;
	}
	
	public ArrayList<HostStatus> getHistory() {
		return history;
	}
	
	public int getEvicting() {
		return evicting;
	}
	
	public void setEvicting(int evicting) {
		this.evicting = evicting;
	}
	
	public ArrayList<Host> getPoweredOffHosts() {
		return poweredOffHosts;
	}
	
	public ArrayList<BidVmEvent> getVmAccepts() {
		return vmAccepts;
	}

	public long getLastShutdownEvent() {
		return lastShutdownEvent;
	}
	
	public void setLastShutdownEvent(long lastShutdownEvent) {
		this.lastShutdownEvent = lastShutdownEvent;
	}
	
	public long getGroupSize() {
		return broadcastingGroup.size();	}
	
	
	public boolean isShuttingDown() {
		return shuttingDown;
	}
	
	public void setShuttingDown(boolean shuttingDown) {
		this.shuttingDown = shuttingDown;
	}
	
	public boolean bidsFrozen() {
		return bidFreeze;
	}
	
	public long getBidFreezeExpiry() {
		return bidFreezeExpiry;
	}
	
	public void enactBidFreeze(long bidFreezeExpiry) {
		this.bidFreeze = true;
		this.bidFreezeExpiry = bidFreezeExpiry;
	}
	
	public void expireBidFreeze() {
		this.bidFreeze = false;
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
}
