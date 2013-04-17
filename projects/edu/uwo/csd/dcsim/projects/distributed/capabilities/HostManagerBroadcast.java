package edu.uwo.csd.dcsim.projects.distributed.capabilities;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.core.SimulationEventBroadcastGroup;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.capabilities.HostManager;
import edu.uwo.csd.dcsim.projects.distributed.events.AcceptVmEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.AdvertiseVmEvent;

public class HostManagerBroadcast extends HostManager {

	public enum ManagementState {NORMAL, SHUTTING_DOWN;}
	
	private SimulationEventBroadcastGroup broadcastingGroup; 
	private ArrayList<HostStatus> history = new ArrayList<HostStatus>();
	private int evicting = 0;
	private VmStatus evictingVm;
	private ManagementState managementState = ManagementState.NORMAL;
	private ArrayList<Host> poweredOffHosts = new ArrayList<Host>();
	private ArrayList<AdvertiseVmEvent> vmAdvertisements = new ArrayList<AdvertiseVmEvent>();
	private ArrayList<AcceptVmEvent> vmAccepts = new ArrayList<AcceptVmEvent>();
	
	public HostManagerBroadcast(Host host, SimulationEventBroadcastGroup broadcastingGroup) {
		super(host);
		
		this.broadcastingGroup = broadcastingGroup;
	}
	
	public void addHistoryStatus(HostStatus status, int windowSize) {
		history.add(0, status);
		if (history.size() > windowSize) {
			history.remove(windowSize - 1);
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
	
	public boolean isEvicting() {
		return evicting != 0;
	}
	
	public void setEvicting(int evicting) {
		this.evicting = evicting;
	}
	
	public ArrayList<Host> getPoweredOffHosts() {
		return poweredOffHosts;
	}
	
	public ArrayList<AdvertiseVmEvent> getVmAdvertisements() {
		return vmAdvertisements;
	}
	
	public ArrayList<AcceptVmEvent> getVmAccepts() {
		return vmAccepts;
	}

}
