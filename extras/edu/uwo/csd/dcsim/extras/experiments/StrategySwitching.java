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
		
		StrategySwitching task = new StrategySwitching("strat-switching");
		
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
		this.setMetricRecordStart(86400000);
	}
	
	@Override
	public void setup(DataCentreSimulation simulation) {
		
		DataCentre dc = DataCentreTestEnvironment.createDataCentre(simulation);
		simulation.addDatacentre(dc);
		ArrayList<VMAllocationRequest> vmList = DataCentreTestEnvironment.createVmList(simulation, true);
		DataCentreTestEnvironment.placeVms(vmList, dc);
		
		DCUtilizationMonitor dcMon = new DCUtilizationMonitor(simulation, 120000, 10, dc);
		simulation.addMonitor(dcMon);
		
		VMAllocationPolicyGreedy vmAllocationPolicy = new VMAllocationPolicyGreedy(dc, 0.6, 0.90, 0.90);
		DaemonScheduler policyDaemon = new FixedIntervalDaemonScheduler(simulation, 600000, vmAllocationPolicy);
		policyDaemon.start(600000);
		
	}

}
