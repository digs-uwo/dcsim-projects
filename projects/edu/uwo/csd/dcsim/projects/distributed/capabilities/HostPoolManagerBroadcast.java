package edu.uwo.csd.dcsim.projects.distributed.capabilities;

import java.util.*;

import edu.uwo.csd.dcsim.core.SimulationEventBroadcastGroup;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.vm.VMAllocationRequest;

public class HostPoolManagerBroadcast extends HostPoolManager {

	private SimulationEventBroadcastGroup broadcastingGroup;
	private Map<VmStatus, VMAllocationRequest> requestMap = new HashMap<VmStatus, VMAllocationRequest>();
	private Map<VmStatus, Integer> requestCounter = new HashMap<VmStatus, Integer>();
	
	private ArrayList<Host> poweredOffHosts = new ArrayList<Host>();
	
	public HostPoolManagerBroadcast() {

	}
	
	public void setBroadcastingGroup(SimulationEventBroadcastGroup broadcastingGroup) {
		this.broadcastingGroup = broadcastingGroup;
	}
	
	public SimulationEventBroadcastGroup getBroadcastingGroup() {
		return broadcastingGroup;
	}
	
	public Map<VmStatus, VMAllocationRequest> getRequestMap() {
		return requestMap;
	}
	
	public Map<VmStatus, Integer> getRequestCounter() {
		return requestCounter;
	}
	
	public ArrayList<Host> getPoweredOffHosts() {
		return poweredOffHosts;
	}
	
}
