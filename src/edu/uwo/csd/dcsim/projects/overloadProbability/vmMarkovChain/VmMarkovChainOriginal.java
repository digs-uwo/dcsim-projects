package edu.uwo.csd.dcsim.projects.overloadProbability.vmMarkovChain;

import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.management.VmStatus;

public class VmMarkovChainOriginal extends VmMarkovChain {

	public VmMarkovChainOriginal(VmStatus vmStatus, Simulation simulation) {
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
		
		//immediately update
		currentState.updateTransitionProbabilities();
		
		//update timestamp
		currentState.lastProbabilityUpdate = simulation.getSimulationTime();
	}
	
}
