package edu.uwo.csd.dcsim.projects.overloadProbability.vmMarkovChain;

import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.management.VmStatus;

public abstract class VmMarkovChain {

	protected Simulation simulation;
	
	protected int id;
	protected UtilizationState[] states = new UtilizationState[5];
	protected UtilizationState currentState = null;
	protected long cpu;
	protected long globalTransitionCount = 0;
	
	public static long nUpdates = 0; //TODO remove
	
	public VmMarkovChain(VmStatus vmStatus, Simulation simulation) {
		this.simulation = simulation;
		
		//initialize states
		states = createUtilizationStates();
		for (UtilizationState state : states) state.initialize();
		
		//set current state
		currentState = states[findState(getUtilization(vmStatus))];
		
		id = vmStatus.getId();
		//cpu = vmStatus.getCoreCapacity() * vmStatus.getCores(); //TODO should this be host core capacity?
		cpu = vmStatus.getHostCoreCapacity() * vmStatus.getCores();
	}
	
	public void recordState(VmStatus vmStatus) {
		UtilizationState nextState = states[findState(getUtilization(vmStatus))];
		
		updateTransitionP(currentState, nextState);
		
		currentState = nextState;
	}
	
	public int getCurrentStateIndex() {
		return findStateIndex(currentState);
	}
	
	protected double getUtilization(VmStatus vm) {
		return vm.getResourcesInUse().getCpu() / (double)(vm.getHostCoreCapacity() * vm.getCores());
	}
	
	protected int findState(double util) {
		for (int i = 0; i < states.length; ++i) {
			if (util <= states[i].rangeUpper) return i;
		}
		throw new RuntimeException("Utilization State not found! util=" + util);
	}
	
	protected int findStateIndex(UtilizationState state) {
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
			System.out.println("nUpdates=" + nUpdates + "\n");
		}
	}
	
	public UtilizationState[] createUtilizationStates() {
		UtilizationState[] states = new UtilizationState[5];
		
		states[0] = new UtilizationState(0, 0.2);
		states[1] = new UtilizationState(0.2, 0.4);
		states[2] = new UtilizationState(0.4, 0.6);
		states[3] = new UtilizationState(0.6, 0.8);
		states[4] = new UtilizationState(0.8, 1.0);		
		
		return states;
	}
	
	protected abstract void updateTransitionP(UtilizationState currentState, UtilizationState toState);
	
	public class UtilizationState {
		protected double rangeLower;
		protected double rangeUpper;

		protected double[] transitionProbabilities = new double[5];
		protected long totalTransitions = 0;
		protected long[] transitionCounts = new long[5];
		
		protected double[] newTransitionProbabilities = new double[5];
		protected long newTotalTransitions = 0;
		protected long[] newTransitionCounts = new long[5];
		
		protected long lastProbabilityUpdate = 0;
		
		
			
		protected UtilizationState(double rangeLower, double rangeUpper) {
			this.rangeLower = rangeLower;
			this.rangeUpper = rangeUpper;
			
			for (int i = 0; i < transitionProbabilities.length; ++i) transitionProbabilities[i] = 0;
		}
		
		/**
		 * Initialize the transition probability of remaining in the same state to '1'
		 */
		public void initialize() {
			transitionProbabilities[findStateIndex(this)] = 1;
		}
		
		protected void updateTransitionProbabilities(){
			transitionProbabilities = newTransitionProbabilities;
			totalTransitions = newTotalTransitions;
			transitionCounts = newTransitionCounts;
			
			nUpdates++;
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
		
		public long getLastProbabilityUpdate() {
			return lastProbabilityUpdate;
		}
				
	}
	
}

