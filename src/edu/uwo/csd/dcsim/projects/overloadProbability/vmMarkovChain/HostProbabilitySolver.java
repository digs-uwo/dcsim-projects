package edu.uwo.csd.dcsim.projects.overloadProbability.vmMarkovChain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import edu.uwo.csd.dcsim.common.Tuple;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.projects.overloadProbability.StressProbabilityMetrics;
import edu.uwo.csd.dcsim.projects.overloadProbability.vmMarkovChain.VmMarkovChain.UtilizationState;


public class HostProbabilitySolver {

	public static final long MAX_STATES = (long)Math.pow(4, 10);
	
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
	
	public double computeStressProbability(HostData host, ArrayList<VmMarkovChain> completeVMlist, double threshold, int filterSize) {
	
		long startTime;
		long endTime;
		
		startTime = System.currentTimeMillis();
		double p = 0;
		long nPerm = 0;
			
		int[] states = new int[completeVMlist.size()];
		VmMarkovChain[] vms = new VmMarkovChain[completeVMlist.size()];
		double[] stateUtil = new double[completeVMlist.size()];
		double[] stateP = new double[completeVMlist.size()];
		int[] vmFilter;
		double sP;
		double util = 0;
		double hostCpu = (double)host.getHostDescription().getResourceCapacity().getCpu();
		
		int[] vmUtil = new int[completeVMlist.size()];
		long nStates = 0;
		
		double pLimit;
		
		//move arraylist into normal array
		{
			int i = 0;
			for (VmMarkovChain vm : completeVMlist) {
				vms[i] = vm;
				states[i] = -1;
				++i;
				vm.resetWorkingTransitions();
			}
		}
		int vmPosition = 0;
		
		for (int i = 0; i < vms.length; ++i) {
			for (VmStatus vm : host.getCurrentStatus().getVms()) {
				if (vm.getId() == vms[i].id) {
					vmUtil[i] = vm.getResourcesInUse().getCpu();
					continue;
				}
			}
		}
		
		//filter VMs
		vmFilter = filterVms(host, vms, vmUtil, threshold, filterSize);
		pLimit = calculateProbabilityLimit(vms);
		
		/*
		 * Main iteration loop. Continue until the VM stack is empty.
		 */
		while (vmPosition >= 0) {
			
			//advance to next state of current VM
			VmMarkovChain currentVm = vms[vmPosition];
			int state = -1;
			
			if (vmFilter[vmPosition] == 1) {
				//VM included in calculation, cycle through possible states
				for (int i = states[vmPosition] + 1; i < currentVm.states.length; ++i) {
					if ((sP = currentVm.currentState.workingTransitionProbabilities[i]) >= pLimit ||
							(currentVm.getCurrentStateIndex() == i)) {
						state = i;
						stateP[vmPosition] = sP; 
						break;
					}
				}
			} else {
				//VM filtered out, so only consider current state
				if (states[vmPosition] == -1) {
					state = currentVm.getCurrentStateIndex();
					stateP[vmPosition] = 1;
				} else {
					state = -1;
				}
			}
			
			//if no state exists, pop and continue
			if (state == -1) {
				--vmPosition;
			} else {				
				states[vmPosition] = state;
				
				if (currentVm.getCurrentStateIndex() == state) {
					//if we are looking at the VMs current state, use it's actual utilization
					stateUtil[vmPosition] = vmUtil[vmPosition];
				} else {
					//otherwise, use the state value
					stateUtil[vmPosition] = currentVm.getCpu() * currentVm.getStates()[state].getValue();
				}

				//if remainingVMs is empty
				if (vmPosition == vms.length - 1) {
					
					++nStates;
					
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
		
		simulation.getSimulationMetrics().getCustomMetricCollection(StressProbabilityMetrics.class).addStateCombinations(nStates);
		simulation.getSimulationMetrics().getCustomMetricCollection(StressProbabilityMetrics.class).addAlgExecTime(endTime - startTime);
		
		return p;
	}

	private int[] filterVms(HostData host, VmMarkovChain[] vmList, int[] vmUtil, double threshold, int filterSize) {
			
		int[] vmFilter = new int[vmList.length];
		double[] vmScore = new double[vmList.length];
		
		//initialize filter
		if (filterSize != -1) {
			for (int i = 0; i < vmFilter.length; ++i) vmFilter[i] = 0;
		} else {
			for (int i = 0; i < vmFilter.length; ++i) vmFilter[i] = 1;
			simulation.getSimulationMetrics().getCustomMetricCollection(StressProbabilityMetrics.class).addVmsInCalc(vmList.length);
			return vmFilter; //filterSize == -1 indicates no filtering
		}

		//first, compute VM "score"
			/*
			 * options for score:
			 * 1. impact: compute "predicted" vm usage (in CPU units, not %), based on all possible state transitions. impact = abs(predicted - current)
			 * 2. negative impact: for each possible *upward* transition, sum transP*(newcpu - current). Need alternate ordering for tie breakers, such as normal 'impact'
			 * 3. amplitude: "predict" for downward and upward change, take sum of absolute values
			 */
		
		//score by potential impact
//		VmMarkovChain vm;
//		double predicted;
//		for (int i = 0; i < vmList.length; ++i) {
//			vmScore[i] = 0;
//			predicted = 0;
//			
//			//iterate through each possible next state
//			for (int s = 0; s < vmList[i].getStates().length; ++s) {
//				predicted += vmList[i].cpu * vmList[i].currentState.transitionProbabilities[s] * vmList[i].getStates()[s].getValue();
//			}
//			
//			vmScore[i] = Math.abs(predicted - vmUtil[i]);
//			
//			if (vmScore[i] < 0) throw new RuntimeException("VM score less than 0!! " + vmScore[i]);
//		}
		
		//score by potential negative impact
		VmMarkovChain vm;
		double predicted;
		for (int i = 0; i < vmList.length; ++i) {
			vmScore[i] = 0;
			predicted = 0;
			
			//iterate through each possible next state
			for (int s = vmList[i].getCurrentStateIndex(); s < vmList[i].getStates().length; ++s) {
				predicted += vmList[i].cpu * vmList[i].currentState.workingTransitionProbabilities[s] * vmList[i].getStates()[s].getValue();
			}
			
			vmScore[i] = Math.abs(predicted - vmUtil[i]);
			
			if (vmScore[i] < 0) throw new RuntimeException("VM score less than 0!! " + vmScore[i]);
		}
		
		//score by amplitude
//		VmMarkovChain vm;
//		double predictedUp;
//		double predictedDown;
//		int currentState;
////		double currentValue;
//		for (int i = 0; i < vmList.length; ++i) {
//			vmScore[i] = 0;
//			predictedUp = 0;
//			predictedDown = 0;
//			currentState = vmList[i].getCurrentStateIndex();			
//			
//			//iterate through each possible next state
//			for (int s = 0; s < vmList[i].getStates().length; ++s) {
//				if (s < currentState) {
//					predictedDown +=  vmList[i].currentState.transitionProbabilities[s] * (vmUtil[i] - (vmList[i].cpu * vmList[i].getStates()[s].getValue()));
//				} else if (s > currentState) {
//					predictedUp += vmList[i].currentState.transitionProbabilities[s] * ((vmList[i].cpu * vmList[i].getStates()[s].getValue()) - vmUtil[i]);					
//				}
//			}
//			
//			vmScore[i] = predictedDown + predictedUp;
//			
//			if (vmScore[i] < 0) throw new RuntimeException("VM score less than 0!! " + vmScore[i]);
//		}
		
		
		
		
		//filter by score, vmFilter[i] = 1 if completeVMlist[i] passes the filter, 0 otherwise
		int count = 0;
		double max;
		int v;
		while (count < filterSize) {
			max = -1;
			v = -1;
			for (int i = 0; i < vmScore.length; ++i) {
				if (vmFilter[i] == 0 && vmScore[i] > max) {
					max = vmScore[i];
					v = i;
				}
			}
			if (max != -1) {
				vmFilter[v] = 1;
				++count;
			} else {
				break; //no more VMs, terminate
			}
		}
		
		if (count < filterSize && vmList.length >= filterSize) {
			System.out.println("nVMs=" + vmList.length);
			System.out.println("count=" + count + ", filterSize=" + filterSize);
			throw new RuntimeException("VM Filtering missed VMs...");
		}
		
		if (vmList.length > filterSize) simulation.getSimulationMetrics().getCustomMetricCollection(StressProbabilityMetrics.class).nOverFilter++;
		
		simulation.getSimulationMetrics().getCustomMetricCollection(StressProbabilityMetrics.class).addFilteredVms(vmList.length - count);
		simulation.getSimulationMetrics().getCustomMetricCollection(StressProbabilityMetrics.class).addVmsInCalc(count);
		
		return vmFilter;
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
			p = p * vm.getCurrentState().getWorkingTransitionProbabilities()[states.get(i)];
			
			if (p == 0) break;
		}
		return p;
	}
	
	private double calculateProbabilityLimit(VmMarkovChain[] vmList) {
		
		double p = 1;
		double max;
		int maxVm;
		int maxState;
		HashSet<UtilizationState> statesIncluded = new HashSet<UtilizationState>();
		int[] vmStates = new int[vmList.length];
		long combinations = 1;
		
		for (int i = 0; i < vmStates.length; ++i) vmStates[i] = 1;
		
		while (combinations <= MAX_STATES) {
			
			max = 0;
			maxVm = -1;
			maxState = -1;
			boolean found = false;
			for (int i = 0; i < vmList.length; ++i) {
				UtilizationState[] states = vmList[i].getStates();
				for (int s = 0; s < states.length; ++s) {
					if (states[s] != vmList[i].getCurrentState()) {
						if (vmList[i].getCurrentState().transitionProbabilities[s] > max && 
								vmList[i].getCurrentState().transitionProbabilities[s] <= p) {
							
							//check if not already included
							if (!statesIncluded.contains(states[s])) {
								//update max
								max = vmList[i].getCurrentState().transitionProbabilities[s];
								maxVm = i;
								maxState = s;
								
								found = true;
							}

						}
					}
				}
			}
			
			//no more states to include
			if (!found) break;
			
			//include new state
			statesIncluded.add(vmList[maxVm].getStates()[maxState]);
			++vmStates[maxVm];
			
			//update p
			p = max;
			
			//update combinations
			combinations = 1;
			for (int i = 0; i < vmStates.length; ++i) combinations *= vmStates[i];
		}
		
		return p;
	}
	
	private void reduceStates(VmMarkovChain[] vmList) {
		ArrayList<Tuple<Integer, Integer>> stateTransitions = new ArrayList<Tuple<Integer, Integer>>();
		int[] vmStates = new int[vmList.length];
		long combinations = 1;
		
		for (int i = 0; i < vmStates.length; ++i) vmStates[i] = 1;
		
		while (combinations <= MAX_STATES) {
			
			for (int i = 0; i < vmList.length; ++i) {
				
			}
			
			//update combinations
			combinations = 1;
			for (int i = 0; i < vmStates.length; ++i) combinations *= vmStates[i];
		}
		
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

