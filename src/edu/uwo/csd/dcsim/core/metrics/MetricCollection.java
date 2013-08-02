package edu.uwo.csd.dcsim.core.metrics;

import edu.uwo.csd.dcsim.core.Simulation;

public abstract class MetricCollection {

	Simulation simulation;
	
	public MetricCollection(Simulation simulation) {
		this.simulation = simulation;
	}
	
	public abstract void completeTimeStep();
	public abstract void completeSimulation();
	
}
