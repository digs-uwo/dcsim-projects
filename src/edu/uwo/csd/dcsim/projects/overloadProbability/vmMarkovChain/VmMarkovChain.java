package edu.uwo.csd.dcsim.projects.overloadProbability.vmMarkovChain;

import edu.uwo.csd.dcsim.management.VmStatus;

public class VmMarkovChain {

	private int id;
	private UtilizationState[] states = new UtilizationState[5];
	private UtilizationState currentState = null;
	private long cpu;
	private long globalTransitionCount = 0;
	
	public VmMarkovChain(VmStatus vmStatus) {
		//initialize states
		states = createUtilizationStates();
		
		//set current state
		currentState = states[findState(getUtilization(vmStatus))];
		
		id = vmStatus.getId();
		cpu = vmStatus.getCoreCapacity() * vmStatus.getCores();
	}
	
	public void recordState(VmStatus vmStatus) {
		UtilizationState nextState = states[findState(getUtilization(vmStatus))];
		
		currentState.updateTransitionProbabilities(nextState);
		
		currentState = nextState;
	}
	
	private double getUtilization(VmStatus vm) {
		return vm.getResourcesInUse().getCpu() / (double)(vm.getCoreCapacity() * vm.getCores());
	}
	
	private int findState(double util) {
		for (int i = 0; i < states.length; ++i) {
			if (util <= states[i].rangeUpper) return i;
		}
		return -1;
	}
	
	private int findStateIndex(UtilizationState state) {
		for (int i = 0; i < states.length; ++i) {
			if (states[i] == state) return i;
		}
		return -1;
	}
	
	public UtilizationState getCurrentState() {
		return currentState;
	}
	
	public UtilizationState[] getStates() {
		return states;
	}
	
	public long getCpu() {
		return cpu;
	}
	
	public int getId() {
		return id;
	}
	
	public void printTransitionMatrix() {
		System.out.println("Based on " + globalTransitionCount + " transitions");
		System.out.printf("             ");
		for (int i = 0; i < states.length; ++i) {
			System.out.printf("[%.1f--%.1f] ", states[i].rangeLower, states[i].rangeUpper);
		}
		System.out.println("");
		
		for (int i = 0; i < states.length; ++i) {
			if (findStateIndex(currentState) == i) {
				System.out.printf("[%.1f--%.1f]*->", states[i].rangeLower, states[i].rangeUpper);
			} else {
				System.out.printf("[%.1f--%.1f] ->", states[i].rangeLower, states[i].rangeUpper);
			}
			for (int j = 0; j < states[i].transitionProbabilities.length; ++j) {
				System.out.printf("   %-8.3f", states[i].transitionProbabilities[j]);
			}
			System.out.println("");
		}
	}
	
	public UtilizationState[] createUtilizationStates() {
		UtilizationState[] states = new UtilizationState[5];
		
//		states[0] = new UtilizationState(0, 0.2);
//		states[1] = new UtilizationState(0.2, 0.5);
//		states[2] = new UtilizationState(0.5, 0.7);
//		states[3] = new UtilizationState(0.7, 0.9);
//		states[4] = new UtilizationState(0.9, 1.0);
		states[0] = new UtilizationState(0, 0.2);
		states[1] = new UtilizationState(0.2, 0.4);
		states[2] = new UtilizationState(0.4, 0.6);
		states[3] = new UtilizationState(0.6, 0.8);
		states[4] = new UtilizationState(0.8, 1.0);		
		
		return states;
	}
	
	public class UtilizationState {
		private double rangeLower;
		private double rangeUpper;

		private double[] transitionProbabilities = new double[5];
		
		
		private long totalTransitions = 0;
		private long[] transitionCounts = new long[5];
			
		private UtilizationState(double rangeLower, double rangeUpper) {
			this.rangeLower = rangeLower;
			this.rangeUpper = rangeUpper;
			
			for (int i = 0; i < transitionProbabilities.length; ++i) transitionProbabilities[i] = 0;
		}
		
		public double getRangeUpper() {
			return rangeUpper;
		}
		
		public double getRangeLower() {
			return rangeLower;
		}
		
		public double getValue() {
			return rangeLower;
//			return (rangeUpper + rangeLower) / 2;
		}
		
		public double[] getTransitionProbabilities() {
			return transitionProbabilities;
		}
		
		public void updateTransitionProbabilities(UtilizationState toState) {
			++transitionCounts[findStateIndex(toState)];
			++totalTransitions;
			++globalTransitionCount;

			for (int i = 0; i < transitionProbabilities.length; ++i) {
				transitionProbabilities[i] = transitionCounts[i] / (double) totalTransitions;
			}
		}
		
	}
	
}
