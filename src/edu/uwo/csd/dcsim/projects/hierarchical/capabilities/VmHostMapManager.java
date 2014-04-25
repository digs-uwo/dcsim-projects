package edu.uwo.csd.dcsim.projects.hierarchical.capabilities;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.uwo.csd.dcsim.common.Tuple;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.capabilities.ManagerCapability;

public class VmHostMapManager extends ManagerCapability {
	
	// Map: { vmId : < hostId , timestamp > }
	private Map<Integer, Tuple<Integer, Long>> vmHostMap = new HashMap<Integer, Tuple<Integer, Long>>();
	private Map<Integer, VmStatus> vms = new HashMap<Integer, VmStatus>();
	private Map<Integer, HostData> knownHosts = new HashMap<Integer, HostData>();
	
	public void addMapping(VmStatus vm, HostData host, long timestamp) {
		Tuple<Integer, Long> t = vmHostMap.get(vm.getId());
		if (null == t || timestamp > t.b)
			vmHostMap.put(vm.getId(), new Tuple<Integer, Long>(host.getId(), timestamp));
		vms.put(vm.getId(), vm);
		knownHosts.put(host.getId(), host);
	}
	
	public void removeMapping(int vmId) {
		vmHostMap.remove(vmId);
		vms.remove(vmId);
	}
	
	public boolean updateMapping(int vmId, int hostId, long timestamp) {
		
		if (vmHostMap.containsKey(vmId) && timestamp > vmHostMap.get(vmId).b) {
			if (knownHosts.containsKey(hostId))
				vmHostMap.put(vmId, new Tuple<Integer, Long>(hostId, timestamp));
			else
				removeMapping(vmId);
			
			return true;
		}
		
		return false;
	}
	
	public HostData getMapping(int vmId) {
		if (vmHostMap.containsKey(vmId))
			return knownHosts.get(vmHostMap.get(vmId).a);
		
		return null;
	}
	
	public VmStatus getVm(int vmId) {
		return vms.get(vmId);
	}
	
	public Collection<VmStatus> getVms() {
		return vms.values();
	}

}
