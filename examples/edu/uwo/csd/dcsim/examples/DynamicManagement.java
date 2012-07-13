package edu.uwo.csd.dcsim.examples;

import java.util.ArrayList;
import java.util.Collection;
import java.io.*;
import java.text.*;

import org.apache.log4j.*;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.core.*;
import edu.uwo.csd.dcsim.core.metrics.*;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.vm.*;
import edu.uwo.csd.dcsim.extras.policies.*;
import edu.uwo.csd.dcsim.extras.experiments.*;



public class DynamicManagement extends DCSimulationTask {
	
	public static final double SLA_COST = 14860.5775413044;
	public static final double POW_COST = 0.08;
	
	//StrategyTree DirNode thresholds
	public static double a, b, c, d;

	private static Logger logger = Logger.getLogger(DynamicManagement.class);
	
	public static void main(String args[]) throws IOException{
		
		Simulation.initializeLogging();
		
		//SimulationExecutor executor = new SimulationExecutor();
				
		File outputFile = new File("results.csv");
		FileWriter out = new FileWriter(outputFile);
		StringBuilder results = new StringBuilder("Results of 8*8*8*8 simulations\n");
//		a = 0.09014145;
//		b = 1.275953;
//		c = 0.09014145;
//		d = 1.275953;
		
		
		a = 0.00003;
		b = 1.90002;
		c = 0.00003;
		d = 1.90002;
		
		long start = System.currentTimeMillis();
		for(int i=0; i<1; i++){
			results.append(run(a,b,c,d));
		}
		long end = System.currentTimeMillis();
		System.out.println("2 runs took: " + ((end-start)/1000) + "s");
		
		//to run 4096 simulations
		/*
		double aStart = 0;
		double bStart = 1.017;
		double cStart = 0;
		double dStart = 1.017;
		
		double aStep, bStep, cStep, dStep;
		aStep = (0.068/7);
		bStep = ((1.477-1.017)/7);
		cStep = (0.068/7);
		dStep = ((1.477-1.017)/7);
		a = aStart;
		for(int i=0; i<8; i++){
			b = bStart;
			for(int j=0; j<8; j++){
				c = cStart;
				for(int k=0; k<8; k++){
					d = dStart;
					for(int l=0; l<8; l++){
						results.append(run(a,b,c,d));
						d += dStep;
					}
					c += cStep;
				}
				b += bStep;
			}
			a += aStep;
		}*/
		
		out.write(results.toString());
		out.close();
		
		/*		
		executor.addTask(new DynamicManagement("dynamic-1", 1088501048448116498l, a, b, c, d));
//		executor.addTask(new DynamicManagement("dynamic-2", 3081198553457496232l));
//		executor.addTask(new DynamicManagement("dynamic-3", -2485691440833440205l));
//		executor.addTask(new DynamicManagement("dynamic-4", 2074739686644571611l));
//		executor.addTask(new DynamicManagement("dynamic-5", -1519296228623429147l));
		
		completedTasks = executor.execute();
		
		for(SimulationTask task : completedTasks) {
			logger.info(task.getName());
			ExampleHelper.printMetrics(task.getResults());
			
			
			//calculate cost of power, sla violations and total cost (sum)
			if(completedTasks.size() == 1){
				double powCost=0, slaCost=0;
				for(Metric metric : task.getResults()){
					if(metric.getName().equals("powerConsumed")){
						//divide by 3600000 to convert from W/s to kW/h
						powCost = (metric.getValue()/3600000) * POW_COST;
					}else if(metric.getName().equals("slaViolation")){
						slaCost = metric.getValue() * SLA_COST;
					}
				}
				logger.info("powerCost = " + powCost);
				logger.info("slaCost = " + slaCost);
				logger.info("totalCost = " + (powCost + slaCost));
			}else{
				System.out.println("Calculation of cost for multiple tasks not supported.");
			}
			System.out.println("******************************************************************");
			
		}
	*/
	}
	
	private static String run(double a, double b, double c, double d){
		Collection<SimulationTask> completedTasks = null;
		SimulationExecutor executor = new SimulationExecutor();
		executor.addTask(new DynamicManagement("dynamic-1", 3081198553457496232l, a, b, c, d));
		completedTasks = executor.execute();
		
		for(SimulationTask task : completedTasks){
			double powCost=0, slaCost=0;
			for(Metric metric : task.getResults()){
				if(metric.getName().equals("powerConsumed")){
					//divide by 3600000 to convert from W/s to kW/h
					powCost = (metric.getValue()/3600000) * POW_COST;
				}else if(metric.getName().equals("slaViolation")){
					slaCost = metric.getValue() * SLA_COST;
					System.out.println("slaViolation showing as: " + metric.getValue());
				}
			}
			DataCentreTestEnvironment.printMetrics(task.getResults());
			logger.info("powerCost = " + powCost);
			logger.info("slaCost = " + slaCost);
			logger.info("totalCost = " + (powCost + slaCost));
			DecimalFormat df = new DecimalFormat("#.#####");
			return df.format(a) + ", " + df.format(b) + ", " + df.format(c) + ", " + df.format(d) + ", " + df.format((powCost+slaCost)) + "\n";
		}
		return "ERROR";
	}
	
	public DynamicManagement(String name, long randomSeed, double a, double b, double c, double d) {
		super(name, 864000000);
		this.setMetricRecordStart(86400000);
		this.setRandomSeed(randomSeed);
	}

	@Override
	public void setup(DataCentreSimulation simulation) {
		
		DataCentre dc = DataCentreTestEnvironment.createDataCentre(simulation);
		simulation.addDatacentre(dc);
		ArrayList<VMAllocationRequest> vmList = DataCentreTestEnvironment.createVmList(simulation, true);
		DataCentreTestEnvironment.placeVms(vmList, dc);
		
		/*
		 * Basic Greedy Relocation & Consolidation together. Relocation same as RelocST03, Consolidation similar but
		 * evicts ALL VMs from underprovisioned hosts, not 1.
		 */
		
		//define the two strategies for the tree
//		VMAllocationPolicyGreedy green = new VMAllocationPolicyGreedy(simulation, dc, 600000, 0.6, 0.95, 0.95);
//		VMAllocationPolicyGreedy greedy = new VMAllocationPolicyGreedy(simulation, dc, 600000, 0.4, 0.75, 0.75);
		//VMAllocationPolicyGreedy balanced = new VMAllocationPolicyGreedy(simulation, dc, 600000, 0.5, 0.85, 0.85);
		
		VMRelocationPolicyGreedy green = new VMRelocationPolicyFFID(simulation, dc, 600000, 0.5, 0.85, 0.85);
		VMRelocationPolicyGreedy greedy = new VMRelocationPolicyFFII(simulation, dc, 600000, 0.5, 0.85, 0.85);
		
		VMConsolidationPolicySimple vmConsolidationPolicy = new VMConsolidationPolicySimple(simulation, dc, 14400000, 0.5, 0.85);
		vmConsolidationPolicy.start(14401000);
		System.out.println("Running now.");
		UpdatePolicy tree = new UpdatePolicy(simulation, dc, green, green, 240000, 0, a, b, c, d);
		tree.start(240000);
	}
	
}
