package edu.uwo.csd.dcsim.extras.experiments;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.uwo.csd.dcsim.DCSimulationTask;
import edu.uwo.csd.dcsim.common.ObjectFactory;
import edu.uwo.csd.dcsim.core.Simulation;

public class SlaPowerStrategyThresholdSearch {

	public static void main(String args[]) {
		
		Simulation.initializeLogging();
		
		StrategyEvaluator stratEval = new StrategyEvaluator(4);
		
		stratEval.addRandomSeed(6198910678692541341l);
		stratEval.addRandomSeed(5646441053220106016l);
		stratEval.addRandomSeed(-5705302823151233610l);
		stratEval.addRandomSeed(8289672009575825404l);
		stratEval.addRandomSeed(-4637549055860880177l);
		
		try{
			File outputFile = new File("slapowerthreshold_results");
			FileWriter out = new FileWriter(outputFile);
			
			out.write("Beginning brute-force search...\n");
			out.flush();
		
			DescriptiveStatistics balancedStats = stratEval.evaluate(
					new ObjectFactory<DCSimulationTask>() {
	
						@Override
						public DCSimulationTask newInstance() {
							return new BalancedStrategy("balanced");
						}
						
					});
			
			out.write("Balanced: " + balancedStats.getMean() + "\n");
			out.flush();
			
			double slaHighValues[] = {0.004, 0.006, 0.008, 0.01};
			double slaNormalValues[] = {0.001, 0.002, 0.004, 0.006};
			double powerHighValues[] = {1.25, 1.3, 1.4, 1.5};
			double powerNormalValues[] = {1.15, 1.2, 1.25, 1.3};
			
			for (double slaHigh : slaHighValues) {
				for (double slaNormal : slaNormalValues) {
					for (double powerHigh : powerHighValues) {
						for (double powerNormal : powerNormalValues) {
							
							if (slaHigh >= slaNormal && powerHigh >= powerNormal) {
							
								//need to create in-scope final variables for the inner class
								final double fSlaHigh = slaHigh;
								final double fSlaNormal = slaNormal;
								final double fPowerHigh = powerHigh;
								final double fPowerNormal = powerNormal;
								
								DescriptiveStatistics switchStats = stratEval.evaluate(
										new ObjectFactory<DCSimulationTask>() {
						
											@Override
											public DCSimulationTask newInstance() {
												return new SlaPowerStrategySwitching("strat-switching", fSlaHigh, fSlaNormal, fPowerHigh, fPowerNormal);
											}
											
										});
								
								
								out.write("Switching (" + fSlaNormal + ", " +
										fSlaHigh + ", " +
										fPowerNormal + ", " +
										fPowerHigh + ")" +									
										": " + switchStats.getMean() + "\n");
								out.flush();
							}
						}
					}
				}
			}
			
			
			out.close();
		
		} catch(IOException e) {
			e.printStackTrace();
		}
		
	}
	
}
