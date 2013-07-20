package edu.uwo.csd.dcsim.application.workload;

import edu.uwo.csd.dcsim.core.Simulation;

/**
 * TwoLevelWorkload keeps a constant workload level until a specified time at which point it switches to a second constant level.
 * 
 * @author Michael Tighe
 *
 */
public class TwoLevelWorkload extends Workload {

	int firstLevel;
	int secondLevel;
	long switchTime;
	int workLevel;
	
	public TwoLevelWorkload(Simulation simulation, int firstLevel, int secondLevel, long switchTime) {
		super(simulation);
		
		this.firstLevel = firstLevel;
		this.secondLevel = secondLevel;
		this.switchTime = switchTime;
		workLevel = firstLevel;
	}
	
	@Override
	protected int getCurrentWorkLevel() {
		return workLevel;
	}

	@Override
	protected long updateWorkLevel() {
		if (simulation.getSimulationTime() == switchTime) {
			workLevel = secondLevel;
			return 0;
		} else {
			return switchTime;
		}
	}
	
	

}
