package edu.uwo.csd.dcsim.application.workload;

import edu.uwo.csd.dcsim.core.Simulation;

/**
 * RandomWorkload generates a random amount of workload, changing on a fixed interval
 * 
 * @author Michael Tighe
 *
 */
public class RandomWorkload extends Workload {

	long stepSize; //the interval on which to change the workload level
	int scaleFactor = 1; //the maximum workload level
	int workLevel = 0; //the current workload level
	
	/**
	 * Create a new RandomWorkload
	 * @param simulation The Simulation this Workload is running in
	 * @param scaleFactor The amount by which to scale the workload level. Workload levels will be in the range [0, scaleFactor]
	 * @param stepSize The time interval on which to generate a new random workload level
	 */
	public RandomWorkload(Simulation simulation, int scaleFactor, long stepSize) {
		super(simulation);	
		
		this.stepSize = stepSize;
		this.scaleFactor = scaleFactor;
		
		workLevel = generateRandomWorkLevel();
	}
	
	public RandomWorkload(Simulation simulation, long stepSize) {
		super(simulation);	
		
		this.stepSize = stepSize;
		
		workLevel = generateRandomWorkLevel();
	}
	
	public void setScaleFactor(int scaleFactor) {
		this.scaleFactor = scaleFactor;
	}
	
	public int getScaleFactor() {
		return scaleFactor;
	}
	
	protected int generateRandomWorkLevel() {
		return (int)Math.round(simulation.getRandom().nextDouble() * scaleFactor);
	}
	
	@Override
	protected int getCurrentWorkLevel() {
		return workLevel;
	}

	@Override
	protected long updateWorkLevel() {
		workLevel = generateRandomWorkLevel();
		return simulation.getSimulationTime() + stepSize;
	}
		
	
}
