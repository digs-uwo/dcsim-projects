package edu.uwo.csd.dcsim.projects.applicationManagement;

import java.util.Collection;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.SimulationExecutor;
import edu.uwo.csd.dcsim.SimulationTask;
import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.capabilities.*;
import edu.uwo.csd.dcsim.management.policies.*;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.ApplicationManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.events.ApplicationPlacementEvent;
import edu.uwo.csd.dcsim.projects.applicationManagement.policies.*;

public class AppManagementExperiment extends SimulationTask {

	private static Logger logger = Logger.getLogger(AppManagementExperiment.class);
	
	private static final long DURATION = SimTime.days(1);
//	private static final long DURATION = SimTime.minutes(5);
	private static final long METRIC_RECORD_START = SimTime.days(0);
	
	public static void main(String args[]) {
		Simulation.initializeLogging();
		
		//broadcast
		Collection<SimulationTask> completedTasks;
		SimulationExecutor executor = new SimulationExecutor();
		
		executor.addTask(new AppManagementExperiment("appManagement-1", 6198910678692541341l));
//		executor.addTask(new AppManagementExperiment("appManagement-2", 5646441053220106016l));
//		executor.addTask(new AppManagementExperiment("appManagement-3", -5705302823151233610l));
//		executor.addTask(new AppManagementExperiment("appManagement-4", 8289672009575825404l));
//		executor.addTask(new AppManagementExperiment("appManagement-5", -4637549055860880177l));
		
		completedTasks = executor.execute();
		
		for(SimulationTask task : completedTasks) {
			logger.info(task.getName());
			task.getMetrics().printDefault(logger);
		}
		
	}
	
	public AppManagementExperiment(String name) {
		super(name, DURATION);
		this.setMetricRecordStart(METRIC_RECORD_START);
	}
	
	public AppManagementExperiment(String name, long randomSeed) {
		super(name, DURATION);
		this.setMetricRecordStart(METRIC_RECORD_START);
		this.setRandomSeed(randomSeed);
	}

	@Override
	public void setup(Simulation simulation) {
		
		simulation.getSimulationMetrics().addCustomMetricCollection(new ApplicationManagementMetrics(simulation));
		
		Environment environment = new Environment(simulation, 5);
		environment.createDataCentre(simulation);
		
		simulation.sendEvent(new ApplicationPlacementEvent(environment.getDcAM(), environment.createApplication()));
		
		simulation.sendEvent(new ApplicationPlacementEvent(environment.getDcAM(), environment.createApplication()), SimTime.minutes(1));
		
		simulation.sendEvent(new ApplicationPlacementEvent(environment.getDcAM(), environment.createApplication()), SimTime.minutes(2));
		
		simulation.sendEvent(new ApplicationPlacementEvent(environment.getDcAM(), environment.createApplication()), SimTime.minutes(3));
	}
	
	public class Environment extends AppManagementTestEnvironment {

		HostPoolManager hostPool;
		
		public Environment(Simulation simulation, int nHosts) {
			super(simulation, nHosts);
		}

		@Override
		public void processDcAM(AutonomicManager dcAM) {
			hostPool = new HostPoolManager();
			dcAM.addCapability(hostPool);
			
			dcAM.installPolicy(new HostStatusPolicy(10));
			dcAM.installPolicy(new ApplicationPlacementPolicy());
			
		}

		@Override
		public void processHost(Host host, DataCentre dc, AutonomicManager dcAM) {
			AutonomicManager hostAM = new AutonomicManager(simulation, new HostManager(host));
			hostAM.installPolicy(new HostMonitoringPolicy(dcAM), SimTime.minutes(5), SimTime.minutes(envRandom.nextInt(5)));
			hostAM.installPolicy(new HostOperationsPolicy());

			host.installAutonomicManager(hostAM);
			
			hostPool.addHost(host, hostAM);
		}

		@Override
		public void processApplication(InteractiveApplication application) {
			AutonomicManager applicationManager = new AutonomicManager(simulation);
			applicationManager.addCapability(new ApplicationManager(application));
			
			applicationManager.installPolicy(new ApplicationScalingPolicy(dcAM), SimTime.minutes(1), 0);
		}
		
	}

}
