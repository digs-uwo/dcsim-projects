package edu.uwo.csd.dcsim.projects.hierarchical.capabilities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.uwo.csd.dcsim.management.capabilities.ManagerCapability;
import edu.uwo.csd.dcsim.projects.hierarchical.VmData;

/**
 * @author Gaston Keller
 *
 */
public class VmPoolManager extends ManagerCapability {
	
	private Map<Integer, VmData> vmMap = new HashMap<Integer, VmData>();
	
	public void addVm(VmData vm) {
		vmMap.put(vm.getId(), vm);
	}
	
	public VmData getVm(int id) {
		return vmMap.get(id);
	}
	
	public Collection<VmData> getVms() {
		return vmMap.values();
	}
	
	public Collection<VmData> getVms(ArrayList<Integer> vmIds) {
		ArrayList<VmData> vms = new ArrayList<VmData>();
		for (int id : vmIds) {
			vms.add(vmMap.get(id));
		}
		return vms;
	}
	
	public void removeVm(int id) {
		vmMap.remove(id);
	}

}
