package edu.uwo.csd.dcsim.projects.overloadProbability.vmMarkovChain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.projects.overloadProbability.StressProbabilityMetrics;
import edu.uwo.csd.dcsim.projects.overloadProbability.vmMarkovChain.VmMarkovChain.UtilizationState;


public class HostProbabilitySolver {

	private Simulation simulation;
	
	private int hostId;
	
	public long n; //this is counting number of checked states, TODO remove it
	public static long nSkipped = 0;
	public static long nTotal = 0;
	
	private Map<String, PCalculation> pCalculations = new HashMap<String, PCalculation>(); //previously completed probability calculations
	private Map<Integer, VmMarkovChain> previousVms = new HashMap<Integer, VmMarkovChain>(); //which VMs the host previously contained -> matches all stored pCalculations
	private double previousThreshold; //the threshold value used with the stored calculations
	
	public HostProbabilitySolver(HostData host, Simulation simulation) {
		this.simulation = simulation;
		hostId = host.getId();
	}
	
	public double computeStressProbability(HostData host, ArrayList<VmMarkovChain> completeVMlist, double threshold) {
	
		long startTime;
		long endTime;
		
		startTime = System.currentTimeMillis();
		double p = 0;
		long nPerm = 0;
		
		Integer[] states = new Integer[completeVMlist.size()];
		VmMarkovChain[] vms = new VmMarkovChain[completeVMlist.size()];
		double[] stateUtil = new double[completeVMlist.size()];
		double[] stateP = new double[completeVMlist.size()];
		double sP;
		double util = 0;
		double hostCpu = (double)host.getHostDescription().getResourceCapacity().getCpu();
		
		{
			int i = 0;
			for (VmMarkovChain vm : completeVMlist) {
				vms[i] = vm;
				states[i] = -1;
				++i;
			}
		}
		int vmPosition = 0;
				
		/*
		 * Main iteration loop. Continue until the VM stack is empty.
		 */
		while (vmPosition >= 0) {
			
			//advance to next state of current VM
			VmMarkovChain currentVm = vms[vmPosition];
			int state = -1;
			for (int i = states[vmPosition] + 1; i < currentVm.states.length; ++i) {
				if ((sP = currentVm.currentState.transitionProbabilities[i]) > 0) {
					state = i;
					stateP[vmPosition] = sP; 
					break;
				}
			}
			
			//if no state exists, pop and continue
			if (state == -1) {
				--vmPosition;
			} else {				
				states[vmPosition] = state;
				stateUtil[vmPosition] = currentVm.getCpu() * currentVm.getStates()[state].getValue();
				
				//if remainingVMs is empty
				if (vmPosition == vms.length - 1) {
					
					//update p with current state information
					util = 0;
					for (double u : stateUtil) util += u;
					util = util / hostCpu;
					if (util >= threshold) {
						sP = 1;
						for (double d : stateP) sP = sP * d;
						p += sP;
					}
					++nPerm;
				} else {
					//move to next VM
					++vmPosition;
					states[vmPosition] = -1;
				}
			}
			
		}
		
		endTime = System.currentTimeMillis();
		
		simulation.getSimulationMetrics().getCustomMetricCollection(StressProbabilityMetrics.class).addAlgExecTime(endTime - startTime);
		
		return p;
	}
	
	private int[] filterVms(HostData host, ArrayList<VmMarkovChain> completeVMlist, double threshold) {
		int[] vmFilter = new int[completeVMlist.size()];
		
		
		//first, compute VM "ranking"
			/*
			 * options for ranking:
			 * 1. impact: compute "predicted" vm usage (in CPU units, not %), based on all possible state transitions. impact = abs(predicted - current)
			 * 2. negative impact: for each possible *upward* transition, sum transP*(newcpu - current). Need alternate ordering for tie breakers, such as normal 'impact'
			 */

		
		//sort by potential ranking
		
		//mark the first 'x' VMs for use
			//vmFilter[i] = 1 if completeVMlist[i] passes the filter, 0 otherwise
		
		return vmFilter;
	}
	
	
	public double computeStressProbabilityRecursive(HostData host, ArrayList<VmMarkovChain> vmMCs, double threshold) {
		n = 0; //TODO remove
		double overloadP;
		
		//check if the host state is different than last time
		overloadP = getExistingCalculation(vmMCs, threshold); 
		if (overloadP != -1) {
			nSkipped++;
		} else {	
			overloadP = computeStressProbabilityRecursive(host, vmMCs, vmMCs, new ArrayList<Integer>(), threshold);
			recordCalculation(vmMCs, overloadP, threshold);
		}
		nTotal++;
//		System.out.println(">>>>>>Skipped = " + nSkipped + "/" + nTotal);
		
		//set previous states to current states
		
		//debugging output, TODO remove
//		if (overloadP >= 0.2) {
//			System.out.println("HOST #" + host.getId() + " with " + host.getCurrentStatus().getVms().size() + " vms, " + 
//					(host.getCurrentStatus().getResourcesInUse().getCpu() / (double)host.getHostDescription().getResourceCapacity().getCpu()) + "util");
//			System.out.println(n + " state combinations");
//			for (VmMarkovChain vm : vmMCs) {
//				System.out.println("----- VM #" + vm.getId() + " -----");
//				vm.printTransitionMatrix();
//				System.out.println("");
//			}
//			System.out.printf("overloadP=%.3f %n", overloadP);
//		}
		
		return overloadP;
	}
	
	private double computeStressProbabilityRecursive(HostData host, ArrayList<VmMarkovChain> vmMCs, ArrayList<VmMarkovChain> completeVmList, ArrayList<Integer> states, double threshold) {
	
		VmMarkovChain vmMC = vmMCs.get(0);
		UtilizationState currentState = vmMC.getCurrentState();
		double p = 0;
		
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
				
				p += computeStressProbabilityRecursive(host, remainingVms, completeVmList, newStates, threshold);
			} else {
				++n;
				if (calculateStateUtilization(newStates, completeVmList, host) >= threshold) {
					p += calculateStateProbability(newStates, completeVmList);				
				}
			}
			
		}

		return p;
		
	}
	
	public double calculateStateUtilization(List<Integer> currentStates, ArrayList<VmMarkovChain> completeVmList, HostData host) {
		double util = 0;

		for (int i = 0; i < currentStates.size(); ++i) {
			VmMarkovChain vm = completeVmList.get(i); 
			util += vm.getCpu() * vm.getStates()[currentStates.get(i)].getValue();
		}
		
		return util / (double)host.getHostDescription().getResourceCapacity().getCpu();
	}
	
	private double calculateStateProbability(List<Integer> states, ArrayList<VmMarkovChain> completeVmList) {
		double p =1 ;
		
		for (int i = 0; i < states.size(); ++i) {
			VmMarkovChain vm = completeVmList.get(i);
			p = p * vm.getCurrentState().getTransitionProbabilities()[states.get(i)];
			
			if (p == 0) break;
		}
		return p;
	}
	
	
	/**
	 * Record a calculation for potential future use.
	 * @param vmList
	 * @param overloadP
	 * @param threshold
	 */
	private void recordCalculation(ArrayList<VmMarkovChain> vmList, double overloadP, double threshold) {
		//repopulate VM list if empty
		if (previousVms.size() == 0) {			
			for (VmMarkovChain vm : vmList) previousVms.put(vm.id, vm);
		}
		
		//record threshold
		previousThreshold = threshold;
		
		//add calculation
		PCalculation c = new PCalculation(vmList, overloadP);
		pCalculations.put(c.code, c);
	}
	
	/**
	 * Look for a previously calculated result for the current situation
	 * @param vmList
	 * @param threshold
	 * @return
	 */
	private double getExistingCalculation(ArrayList<VmMarkovChain> vmList, double threshold) {
		boolean valid = true;
		double p = -1;
		
		/*
		 * First we need to verify that the previously stored calculations are still valid.
		 * They are NOT valid if:
		 *  -the probability threshold has changed;
		 *  -the host now has a different number of VMs;
		 *  -or the host has different VMs.
		 *  
		 *  If the stored values are found to be invalid, we clear them all and start fresh.
		 */
		
		//verify threshold
		if (threshold != previousThreshold) valid = false;
		
		//verify number of VMs
		if (vmList.size() != previousVms.size()) valid = false;
		
		//verify which VMs
		for (VmMarkovChain vm : vmList) {
			if (!previousVms.containsKey(vm.id)) valid = false;
		}

		if (!valid) {
			//if not valid, clear all
			pCalculations.clear();
			previousVms.clear();
		} else {
			
			/*
			 * The set of stored calculations is potentially valid. We look for this particular state combination,
			 * using the vm state encoding.
			 */
			
			//if valid, look for existing calculation
			String code = encodeVmStates(vmList);
			
			PCalculation calc = pCalculations.get(code);
			if (calc != null) {
				
				/*
				 * Finally, we need to verify that none of the current utilization state transition probabilities
				 * have been updated *after* the calculation. If so, we throw out the calculation.
				 */
				
				//verify its timestamp compared to all vm state timestamps
				valid = true;
				for (VmMarkovChain vm : vmList) {
					if (vm.getCurrentState().getLastProbabilityUpdate() > calc.timestamp) valid = false;
				}
				
				if (!valid) {
					//if not valid, remove
					pCalculations.remove(code);
				} else {
					//if valid, return value
					p = calc.overloadP;
				}
			}
		}
		
		return p;
	}
	
	/**
	 * Build a String encoding of the current states of a list of VMs. List is first sorted by
	 * VM id, and then encoded as a string.
	 * @param vmList
	 * @return
	 */
	private String encodeVmStates(ArrayList<VmMarkovChain> vmList) {
		char[] code = new char[vmList.size()];
		
		//sort vm list
		ArrayList<VmMarkovChain> sorted = new ArrayList<VmMarkovChain>();
		sorted.addAll(vmList);
		Collections.sort(sorted, new Comparator<VmMarkovChain>() {

			@Override
			public int compare(VmMarkovChain arg0, VmMarkovChain arg1) {
				return arg1.id - arg0.id;
			}
			
		});
		
		//build string code
		for (int i = 0; i < sorted.size(); ++i) {
			code[i] = (char)(sorted.get(i).getCurrentStateIndex() + 97); //48 for numbers, but this allows more states while printable for debugging
		}
		
		return new String(code);
	}
	
	/**
	 * Represents a previously completed probability calculation
	 * @author michael
	 *
	 */
	public class PCalculation {
		long timestamp;
		String code;
		double overloadP;
		
		public PCalculation(ArrayList<VmMarkovChain> vmList, double overloadP) {
			this.timestamp = simulation.getSimulationTime();
			this.code = encodeVmStates(vmList);
			this.overloadP = overloadP;
		}
		
	}
	
}

