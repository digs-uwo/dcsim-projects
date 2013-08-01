package edu.uwo.csd.dcsim.core.metrics;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.core.Simulation;

public abstract class MetricCollection {

	Simulation simulation;
	ArrayList<Double> timeWeights = new ArrayList<Double>();
	
	public MetricCollection(Simulation simulation) {
		this.simulation = simulation;
	}
	
	public void updateTimeWeights() {
		timeWeights.add((double)simulation.getElapsedTime());
	}
	
	public ArrayList<Double> getTimeWeights() {
		return timeWeights;
	}
	
	public double[] toDoubleArray(ArrayList<Double> list) {
		double[] array = new double[list.size()];
		
		for (int i = 0; i < list.size(); ++i) {
			array[i] = list.get(i);
		}
		
		return array;
	}
	
	public abstract void completeTimeStep();
	public abstract void completeSimulation();
	
}
