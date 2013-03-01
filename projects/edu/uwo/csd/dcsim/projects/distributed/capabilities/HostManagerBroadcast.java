package edu.uwo.csd.dcsim.projects.distributed.capabilities;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.capabilities.HostManager;

public class HostManagerBroadcast extends HostManager {

	private ArrayList<HostStatus> history = new ArrayList<HostStatus>();
	private int evicting = 0;
	
	public HostManagerBroadcast(Host host) {
		super(host);
	}
	
	public void addHistoryStatus(HostStatus status, int windowSize) {
		history.add(0, status);
		if (history.size() > windowSize) {
			history.remove(windowSize - 1);
		}
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

}
