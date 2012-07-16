package edu.uwo.csd.dcsim.extras.experiments;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.core.DaemonScheduler;
import edu.uwo.csd.dcsim.core.FixedIntervalDaemonScheduler;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.extras.policies.*;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.vm.VMAllocationRequest;

public class StrategySwitching extends DCSimulationTask {

	public static void main(String args[]) {
		
		Simulation.initializeLogging();
		
		StrategySwitching task = new StrategySwitching("strat-switching", 6198910678692541341l);
//		StrategySwitching task = new StrategySwitching("strat-switching", 5646441053220106016l);
//		StrategySwitching task = new StrategySwitching("strat-switching", -5705302823151233610l);
//		StrategySwitching task = new StrategySwitching("strat-switching", 8289672009575825404l);
//		StrategySwitching task = new StrategySwitching("strat-switching", -4637549055860880177l);
		
		task.run();
		
		DataCentreTestEnvironment.printMetrics(task.getResults());
		
	}
	
	public StrategySwitching(String name, long randomSeed) {
		super(name, 864000000);
		this.setMetricRecordStart(86400000);
		this.setRandomSeed(randomSeed);
	}

	
	public StrategySwitching(String name) {
		super(name, 864000000);
		this.setMetricRecordStart(0);
	}
	
	@Override
	public void setup(DataCentreSimulation simulation) {
		
		DataCentre dc = DataCentreTestEnvironment.createDataCentre(simulation);
		simulation.addDatacentre(dc);
		ArrayList<VMAllocationRequest> vmList = DataCentreTestEnvironment.createVmList(simulation, true);
		DataCentreTestEnvironment.placeVms(vmList, dc);
		
		DCUtilizationMonitor dcMon = new DCUtilizationMonitor(simulation, 120000, 10, dc);
		simulation.addMonitor(dcMon);
		
		VMAllocationPolicyGreedy powerPolicy = new VMAllocationPolicyGreedy(dc, 0.6, 0.90, 0.90);
		VMAllocationPolicyGreedy slaPolicy = new VMAllocationPolicyGreedy(dc, 0.4, 0.75, 0.75);
		
		SlaVsPowerSwitchingPolicy switchingPolicy = new SlaVsPowerSwitchingPolicy.Builder(dcMon).slaPolicy(slaPolicy)
				.powerPolicy(powerPolicy)
				.switchingInterval(3600000)
				.slaHigh(0.01)
				.slaNormal(0.005)
				.powerHigh(1.4)
				.powerNormal(1.3)
				.optimalPowerPerCpu(0.01165)
				.build();
		
		
		
		DaemonScheduler policyDaemon = new FixedIntervalDaemonScheduler(simulation, 600000, switchingPolicy);
		policyDaemon.start(600000);
		
		
		
		
	}

}
