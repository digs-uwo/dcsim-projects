package edu.uwo.csd.dcsim.projects.overloadProbability.capabilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.capabilities.ManagerCapability;
import edu.uwo.csd.dcsim.projects.overloadProbability.vmMarkovChain.*;

public class VmMarkovChainManager extends ManagerCapability {
	
	private double upperThreshold;
	private int filterSize;
	
	private Map<Integer, HostProbabilitySolver> hostSolvers = new HashMap<Integer, HostProbabilitySolver>();
	private Map<Integer, VmMarkovChain> vmMCs = new HashMap<Integer, VmMarkovChain>();
	
	public VmMarkovChainManager(double upperThreshold, int filterSize) {
		this.upperThreshold = upperThreshold;
		this.filterSize = filterSize;
	}
	

	public void updateHost(HostData host, Simulation simulation) {
		
		//ensure there is a HostProbabilitySolver created for this host
		if (!hostSolvers.containsKey(host.getId())) hostSolvers.put(host.getId(), new HostProbabilitySolver(host, simulation));
		
		//update individual VM markov chains
		for (VmStatus vm : host.getCurrentStatus().getVms()) updateVm(vm, simulation);
	}
	
	private void updateVm(VmStatus vm, Simulation simulation) {
		VmMarkovChain vmMC;
		if (vmMCs.containsKey(vm.getId())) {
			vmMC = vmMCs.get(vm.getId());
			vmMC.recordState(vm);
		} else {
			//vmMC = new VmMarkovChainOriginal(vm, simulation);
			vmMC = new VmMarkovChainUpdateOnChange(vm, simulation);
			vmMCs.put(vm.getId(), vmMC);
		}
	}
	
	public double calculateOverloadProbability(HostData host) {
		ArrayList<VmMarkovChain> vmList = new ArrayList<VmMarkovChain>();
		for (VmStatus vm : host.getCurrentStatus().getVms()) {
			vmList.add(vmMCs.get(vm.getId()));
		}
		
		return hostSolvers.get(host.getId()).computeStressProbability(host, vmList, upperThreshold, filterSize);
	}
	
}
