package edu.uwo.csd.dcsim.examples;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.application.workload.*;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.metrics.Metric;
import edu.uwo.csd.dcsim.examples.management.ConsolidationPolicy;
import edu.uwo.csd.dcsim.examples.management.RelocationPolicy;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.HostModels;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.host.power.LinearHostPowerModel;
import edu.uwo.csd.dcsim.host.resourcemanager.DefaultResourceManagerFactory;
import edu.uwo.csd.dcsim.host.scheduler.DefaultResourceSchedulerFactory;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.capabilities.*;
import edu.uwo.csd.dcsim.management.events.VmPlacementEvent;
import edu.uwo.csd.dcsim.management.policies.*;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;

public class ApplicationExample extends SimulationTask {

	private static Logger logger = Logger.getLogger(ApplicationExample.class);
	
	public static void main(String args[]) {
	
		Simulation.initializeLogging();
		
		SimulationTask task = new ApplicationExample("AppExample", SimTime.minutes(5));
		
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
	}

	@Override
	public void setup(Simulation simulation) {
		
		DataCentre dc = new DataCentre(simulation);
		
		//Add the DataCentre to the simulation
		simulation.addDatacentre(dc);
		
		//Create the HostPoolManager capability separately, as we need to reference it later to add hosts
		HostPoolManager hostPool = new HostPoolManager();
		
		//Create a new AutonomicManager with this capability
		AutonomicManager dcAM = new AutonomicManager(simulation, hostPool);
		
		//Install the HostStatusPolicy and VmPlacementPolicy
		dcAM.installPolicy(new HostStatusPolicy(5));
		dcAM.installPolicy(new DefaultVmPlacementPolicy());
		
		//Create hosts
		
		Host.Builder hostBuilder = HostModels.ProLiantDL160G5E5420(simulation).privCpu(500).privBandwidth(131072)
				.resourceManagerFactory(new DefaultResourceManagerFactory())
				.resourceSchedulerFactory(new DefaultResourceSchedulerFactory());
		
		//Instantiate the Hosts
		ArrayList<Host> hosts = new ArrayList<Host>();
		for (int i = 0; i < 1; ++i) {
			Host host = hostBuilder.build();
			
			//Create an AutonomicManager for the Host, with the HostManager capability (provides access to the host being managed)
			AutonomicManager hostAM = new AutonomicManager(simulation, new HostManager(host));
			
			//Install a HostMonitoringPolicy, which sends status updates to the datacentre manager, set to execute every 5 minutes
			hostAM.installPolicy(new HostMonitoringPolicy(dcAM), SimTime.minutes(5), 0);
			
			//Install a HostOperationsPolicy, which handles basic host operations
			hostAM.installPolicy(new HostOperationsPolicy());
			
			//Optionally, we can "install" the manager into the Host. This ensures that the manager does not run when the host is
			//not 'ON', and triggers hooks in the manager and policies on power on and off.
			host.installAutonomicManager(hostAM);
			
//			host.setState(Host.HostState.ON);
			
			//Add the Host to the DataCentre
			dc.addHost(host);
			
			//Add the Host to the HostPoolManager capability of our datacentre AutonomicManager
			hostPool.addHost(host, hostAM);
			
			hosts.add(host);
		}
		
		//Create applications
		ArrayList<VmAllocationRequest> vmRequests = new ArrayList<VmAllocationRequest>();
		
		for (int i = 0; i < 1; ++i) {
			Workload workload = new StaticWorkload(simulation, 100);
//			Workload workload = new TraceWorkload(simulation, "traces/clarknet", 100, (int)(simulation.getRandom().nextDouble() * 200000000));
			InteractiveApplication.Builder appBuilder = new InteractiveApplication.Builder(simulation).workload(workload).thinkTime(4)
					.task(1, new Resources(2000,1,1,1), 0.05f, 1)
					.task(1, new Resources(2000,1,1,1), 0.2f, 1)
					.task(1, new Resources(2000,1,1,1), 0.1f, 1);
			
			InteractiveApplication app = appBuilder.build();
			
			//place applications
			vmRequests.addAll(app.createInitialVmRequests());

		}
		
		VmPlacementEvent vmPlacementEvent = new VmPlacementEvent(dcAM, vmRequests);
		simulation.sendEvent(vmPlacementEvent, 0);
		
		dcAM.installPolicy(new RelocationPolicy(0.5, 0.9, 0.85), SimTime.hours(1), SimTime.hours(1) + 1);
		dcAM.installPolicy(new ConsolidationPolicy(0.5, 0.9, 0.85), SimTime.hours(2), SimTime.hours(2) + 2);
	}
	
}

