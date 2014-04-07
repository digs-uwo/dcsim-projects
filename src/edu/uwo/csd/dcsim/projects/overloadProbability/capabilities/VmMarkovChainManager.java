package edu.uwo.csd.dcsim.projects.overloadProbability.capabilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.capabilities.ManagerCapability;
import edu.uwo.csd.dcsim.projects.overloadProbability.vmMarkovChain.*;

public class VmMarkovChainManager extends ManagerCapability {
	
	private double upperThreshold;
	
	private Map<Integer, VmMarkovChain> vmMCs = new HashMap<Integer, VmMarkovChain>();
	
	public VmMarkovChainManager(double upperThreshold) {
		this.upperThreshold = upperThreshold;
	}
	
	public void updateHosts(List<HostData> hosts) {
		for (HostData host : hosts) updateHost(host);
	}
	
	public void updateHost(HostData host) {
		for (VmStatus vm : host.getCurrentStatus().getVms()) updateVm(vm);
	}
	
	public void updateVm(VmStatus vm) {
		VmMarkovChain vmMC;
		if (vmMCs.containsKey(vm.getId())) {
			vmMC = vmMCs.get(vm.getId());
			vmMC.recordState(vm);
		} else {
			vmMC = new VmMarkovChain(vm);
			vmMCs.put(vm.getId(), vmMC);
		}
	}
	
	public double calculateOverloadProbability(HostData host) {
		ArrayList<VmMarkovChain> vmList = new ArrayList<VmMarkovChain>();
		for (VmStatus vm : host.getCurrentStatus().getVms()) {
			vmList.add(vmMCs.get(vm.getId()));
		}
		
		return HostProbabilitySolver.computeStressProbability(host, vmList, upperThreshold);
	}
	
}
