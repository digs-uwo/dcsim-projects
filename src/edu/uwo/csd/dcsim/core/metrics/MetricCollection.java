package edu.uwo.csd.dcsim.core.metrics;

import java.io.PrintStream;

import edu.uwo.csd.dcsim.core.Simulation;

public abstract class MetricCollection {

	Simulation simulation;
	
	public MetricCollection(Simulation simulation) {
		this.simulation = simulation;
	}
	
	public abstract void completeSimulation();
	
	public abstract void printDefault(PrintStream out);
}
