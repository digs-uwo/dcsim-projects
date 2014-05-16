package edu.uwo.csd.dcsim.projects.overloadProbability.vmMarkovChain;

import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.management.VmStatus;

public class VmMarkovChainUpdateOnChange extends VmMarkovChain {

	public static double CHANGE_T = 0.1;
	public static long INIT_TRANSITIONS = 10;
	
	public VmMarkovChainUpdateOnChange(VmStatus vmStatus, Simulation simulation) {
		super(vmStatus, simulation);
	}

	protected void updateTransitionP(UtilizationState currentState, UtilizationState toState) {
		//update count values
		++currentState.newTransitionCounts[findStateIndex(toState)];
		++currentState.newTotalTransitions;
		++globalTransitionCount;
		
		//calculate new transition matrix
		for (int i = 0; i < currentState.newTransitionProbabilities.length; ++i) {
			currentState.newTransitionProbabilities[i] = currentState.newTransitionCounts[i] / (double) currentState.newTotalTransitions;
		}

		//don't allow live calculated transition probabilities until after a minimum number of recorded transitions 
		if (globalTransitionCount > INIT_TRANSITIONS) {
			boolean changed = false;
			//check to see if any transition probabilities have changed significantly
			for (int i = 0; i < currentState.newTransitionProbabilities.length; ++i) {
				double diff = Math.abs(currentState.newTransitionProbabilities[i] - currentState.transitionProbabilities[i]);  
				if (diff >= CHANGE_T) changed = true;
			}
			
			//if changed, update for all UtilizationStates
			if (changed) {
				currentState.updateTransitionProbabilities();
				
				//update timestamp
				currentState.lastProbabilityUpdate = simulation.getSimulationTime();
			}
		}
	}
	
}
