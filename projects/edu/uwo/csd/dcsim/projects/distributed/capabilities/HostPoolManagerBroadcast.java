package edu.uwo.csd.dcsim.projects.distributed.capabilities;

import java.util.*;

import edu.uwo.csd.dcsim.core.SimulationEventBroadcastGroup;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.projects.distributed.Eviction;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;

public class HostPoolManagerBroadcast extends HostPoolManager {

	private SimulationEventBroadcastGroup broadcastingGroup;
	
	private long lastBoot = -1000000000;
	
	private ArrayList<Eviction> evictions = new ArrayList<Eviction>();
	private Map<Eviction, VmAllocationRequest> requestMap = new HashMap<Eviction, VmAllocationRequest>();
	
	private ArrayList<Host> poweredOffHosts = new ArrayList<Host>();
	
	public HostPoolManagerBroadcast() {

	}
	
	public void setBroadcastingGroup(SimulationEventBroadcastGroup broadcastingGroup) {
		this.broadcastingGroup = broadcastingGroup;
	}
	
	public SimulationEventBroadcastGroup getBroadcastingGroup() {
		return broadcastingGroup;
	}
	
	public ArrayList<Eviction> getEvictions() {
		return evictions;
	}
	
	public ArrayList<Host> getPoweredOffHosts() {
		return poweredOffHosts;
	}
	
	public long getLastBoot() {
		return lastBoot;
	}
	
	public void setLastBoot(long lastBoot) {
		this.lastBoot = lastBoot;
	}
	
	public void addRequest(VmAllocationRequest request, Eviction eviction) {
		requestMap.put(eviction, request);
	}
	
	public VmAllocationRequest getRequest(Eviction eviction) {
		return requestMap.get(eviction);
	}
	
	public void clearRequest(Eviction eviction) {
		requestMap.remove(eviction);
	}
	
	public void addEviction(Eviction eviction, VmAllocationRequest request) {
		evictions.add(eviction);
		requestMap.put(eviction, request);
	}
	
	public void clearEviction(Eviction eviction) {
		evictions.remove(eviction);
		clearRequest(eviction);
	}
	
}
