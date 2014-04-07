package edu.uwo.csd.dcsim.projects.overloadProbability.vmMarkovChain;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.projects.overloadProbability.vmMarkovChain.VmMarkovChain.UtilizationState;


public class HostProbabilitySolver {

	public static double overloadP = 0;
	public static ArrayList<VmMarkovChain> completeVmList;
	
	public static long n;
	
	public static double computeStressProbability(HostData host, ArrayList<VmMarkovChain> vmMCs, double threshold) {
		n = 0;
		overloadP = 0;
		completeVmList = vmMCs;
		computeStressProbability(host, vmMCs, new ArrayList<Integer>(), threshold);
		
		if (overloadP >= 0.2) {
			System.out.println("HOST #" + host.getId() + " with " + host.getCurrentStatus().getVms().size() + " vms, " + 
					(host.getCurrentStatus().getResourcesInUse().getCpu() / (double)host.getHostDescription().getResourceCapacity().getCpu()) + "util");
			System.out.println(n + " state combinations");
			for (VmMarkovChain vm : vmMCs) {
				System.out.println("----- VM #" + vm.getId() + " -----");
				vm.printTransitionMatrix();
				System.out.println("");
			}
			System.out.printf("overloadP=%.3f %n", overloadP);
		}
		
		return overloadP;
	}
	
	private static void computeStressProbability(HostData host, ArrayList<VmMarkovChain> vmMCs, ArrayList<Integer> states, double threshold) {
	
		VmMarkovChain vmMC = vmMCs.get(0);
		UtilizationState currentState = vmMC.getCurrentState();
		for (int i = 0; i < vmMC.getStates().length; ++i) {
			
			//if there is no transition to this state, continue
			if (currentState.getTransitionProbabilities()[i] == 0) continue;
			
			//create a new list of states
			ArrayList<Integer> newStates = new ArrayList<Integer>();
			newStates.addAll(states);
			
			//add current state to list
			newStates.add(i);
			
			//find all possible states of other VMs
			if (vmMCs.size() > 1) {
				ArrayList<VmMarkovChain> remainingVms = new ArrayList<VmMarkovChain>();
				remainingVms.addAll(vmMCs.subList(1, vmMCs.size()));
				
				computeStressProbability(host, remainingVms, newStates, threshold);
			} else {
				++n;
				if (calculateStateUtilization(newStates, host) >= threshold) {
					overloadP += calculateStateProbability(newStates);
				}
			}
			
		}

	}
	
	public static double calculateStateUtilization(ArrayList<Integer> currentStates, HostData host) {
		double util = 0;

		for (int i = 0; i < currentStates.size(); ++i) {
			VmMarkovChain vm = completeVmList.get(i); 
			util += vm.getCpu() * vm.getStates()[currentStates.get(i)].getValue();
		}
		
		return util / (double)host.getHostDescription().getResourceCapacity().getCpu();
	}
	
	private static double calculateStateProbability(ArrayList<Integer> states) {
		double p =1 ;
		
		for (int i = 0; i < states.size(); ++i) {
			VmMarkovChain vm = completeVmList.get(i);
			p = p * vm.getCurrentState().getTransitionProbabilities()[states.get(i)];
			
			if (p == 0) break;
		}
		return p;
	}
	
}
