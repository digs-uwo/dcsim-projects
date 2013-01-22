package edu.uwo.csd.dcsim.extras.experiments;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.uwo.csd.dcsim.DCSimulationTask;
import edu.uwo.csd.dcsim.common.ObjectFactory;
import edu.uwo.csd.dcsim.core.Simulation;

public class PseudoEvaluator {

	public static void main(String args[]) {

		Simulation.initializeLogging();
		
		StrategyEvaluator stratEval = new StrategyEvaluator(4);
		
		stratEval.generateRandomSeeds(10, 6198910678692541341l);	
		
		DescriptiveStatistics slaStats = stratEval.evaluateInChunks(4,
				new ObjectFactory<DCSimulationTask>() {

					@Override
					public DCSimulationTask newInstance() {
						return new SLAFriendlyStrategy("sla", 0);
					}
					
				});
		
		DescriptiveStatistics powerStats = stratEval.evaluateInChunks(4,
				new ObjectFactory<DCSimulationTask>() {

					@Override
					public DCSimulationTask newInstance() {
						return new GreenStrategy("power", 0);
					}
					
				});
		
		DescriptiveStatistics hybridStats = stratEval.evaluateInChunks(4,
				new ObjectFactory<DCSimulationTask>() {

					@Override
					public DCSimulationTask newInstance() {
						return new BalancedStrategy("hybrid");
					}
					
				});
	
//		DescriptiveStatistics spStats = stratEval.evaluateInChunks(4,
//				new ObjectFactory<DCSimulationTask>() {
//
//					@Override
//					public DCSimulationTask newInstance() {
//						return new SlaPowerStrategySwitching("sp-dss", 0.008, 0.004, 1.3, 1.15);
//					}
//					
//				});
		
//		DescriptiveStatistics utilStats = stratEval.evaluateInChunks(4,
//				new ObjectFactory<DCSimulationTask>() {
//
//					@Override
//					public DCSimulationTask newInstance() {
//						//UtilStrategySwitching thresholds: toPower=0.00255, toSla=0.00255
//						return new UtilStrategySwitching("util-dss", 0, 0.00255, 0.00255);
//					}
//					
//				});
		
		DescriptiveStatistics goalStats = stratEval.evaluateInChunks(4,
				new ObjectFactory<DCSimulationTask>() {

					@Override
					public DCSimulationTask newInstance() {
						// Parameter values for when SLA and Power are running with their original threshold values.
						// worstSla= 0.01, worstEfficiency= 0.83
						return new DistanceToGoalStrategySwitching("goal-dss", 0, 0.01, 0.83);
						
						// Parameter values for when SLA and Power are running with Hybrid's threshold values.
						// worstSla=0.0005 , worstEfficiency=0.82
						//return new DistanceToGoalStrategySwitching("goal-dss", 0, 0.0005, 0.82);
					}
					
				});
		
		System.out.println("SLA: " + slaStats.getMean() + "\n");
		System.out.println("Power: " + powerStats.getMean() + "\n");
		System.out.println("Hybrid: " + hybridStats.getMean() + "\n");
//		System.out.println("SP-DSS: " + spStats.getMean() + "\n");
//		System.out.println("Util-DSS: " + utilStats.getMean() + "\n");
		System.out.println("Goal-DSS: " + goalStats.getMean() + "\n");
	
		
	}
	
}
