package edu.uwo.csd.dcsim.examples;

import java.util.Collection;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.SimulationTask;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.metrics.Metric;

public class ApplicationExample extends SimulationTask {

	private static Logger logger = Logger.getLogger(ApplicationExample.class);
	
	public static void main(String args[]) {
	
		Simulation.initializeLogging();
		
		SimulationTask task = new ApplicationExample("AppExample", SimTime.minutes(1));
		
		task.run();
		
		//get the results of the simulation
		Collection<Metric> metrics = task.getResults();
		
		//output metric values
		for (Metric metric : metrics) {
			logger.info(metric.getName() + "=" + metric.toString()); //metric.getValue() returns the raw value, while toString() provides formatting
		}
		
	}
	
	public ApplicationExample(String name, long duration) {
		super(name, duration);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void setup(Simulation simulation) {
		// TODO Auto-generated method stub
		
	}
	
}
