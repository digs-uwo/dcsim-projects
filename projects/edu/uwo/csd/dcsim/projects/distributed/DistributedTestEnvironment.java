package edu.uwo.csd.dcsim.projects.distributed;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;

import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.application.ApplicationGeneratorLegacy;
import edu.uwo.csd.dcsim.application.Applications;
import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.application.workload.TraceWorkload;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.common.Tuple;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.SimulationEventBroadcastGroup;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.HostModels;
import edu.uwo.csd.dcsim.host.resourcemanager.DefaultResourceManagerFactory;
import edu.uwo.csd.dcsim.host.scheduler.DefaultResourceSchedulerFactory;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.capabilities.HostManager;
import edu.uwo.csd.dcsim.management.policies.HostOperationsPolicy;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.HostManagerBroadcast;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.HostPoolManagerBroadcast;
import edu.uwo.csd.dcsim.projects.distributed.policies.HostMonitoringPolicyBroadcast;

public class DistributedTestEnvironment {

	public static final int N_HOSTS = 200; // 2000
	
	public static final int CPU_OVERHEAD = 200;
	public static final int[] VM_SIZES = {1500, 2500, 2500};
	public static final int[] VM_CORES = {1, 1, 2};
	public static final int[] VM_RAM = {512, 1024, 1024};
	public static final int N_VM_SIZES = 3;
	
	public static final int N_TRACES = 5; 
	public static final String[] TRACES = {"traces/clarknet", 
		"traces/epa",
		"traces/sdsc",
		"traces/google_cores_job_type_0", 
		"traces/google_cores_job_type_1",
		"traces/google_cores_job_type_2",
		"traces/google_cores_job_type_3"};	
	public static final long[] OFFSET_MAX = {200000000, 40000000, 40000000, 15000000, 15000000, 15000000, 15000000};
	public static final double[] TRACE_AVG = {0.32, 0.25, 0.32, 0.72, 0.74, 0.77, 0.83};
	
	/**
	 * Creates a DataCentre object and its corresponding AutonomicManager 
	 * object. The data centre consists of a collection of hosts.
	 * 
	 * This method also instantiates the ServiceProducer.
	 * 
	 * @param simulation
	 * @return		the built data centre
	 */
	public static Tuple<DataCentre, AutonomicManager> createDataCentre(Simulation simulation, double lower, double upper, double target) {
		// Create data centre.
		DataCentre dc = new DataCentre(simulation);
		simulation.addDatacentre(dc);
		
		HostPoolManagerBroadcast hostPool = new HostPoolManagerBroadcast();
		AutonomicManager dcAM = new AutonomicManager(simulation, hostPool);
		
		// Create hosts and add to data centre.
		createHosts(simulation, dc, dcAM, lower, upper, target);
		
		return new Tuple<DataCentre, AutonomicManager>(dc, dcAM);
	}
	
	/**
	 * Creates the collection of hosts for the data centre. The hosts are 
	 * equally divided between the two available models: proLiantDL360G5E5450 
	 * and proLiantDL160G5E5420.
	 */
	private static void createHosts(Simulation simulation, DataCentre dataCentre, AutonomicManager dcAM, double lower, double upper, double target) {
		
		HostPoolManagerBroadcast hostPool = dcAM.getCapability(HostPoolManagerBroadcast.class);
		SimulationEventBroadcastGroup broadcastingGroup = new SimulationEventBroadcastGroup();
		broadcastingGroup.addMember(dcAM);
		hostPool.setBroadcastingGroup(broadcastingGroup);
		
		for (int i = 0; i < N_HOSTS; ++i) {
			Host host;
			
			Host.Builder proLiantDL380G5QuadCore = HostModels.ProLiantDL380G5QuadCore(simulation).privCpu(500).privBandwidth(131072)
					.resourceManagerFactory(new DefaultResourceManagerFactory())
					.resourceSchedulerFactory(new DefaultResourceSchedulerFactory());
			
			Host.Builder proLiantDL160G5E5420 = HostModels.ProLiantDL160G5E5420(simulation).privCpu(500).privBandwidth(131072)
					.resourceManagerFactory(new DefaultResourceManagerFactory())
					.resourceSchedulerFactory(new DefaultResourceSchedulerFactory());
			
			if (i % 2 == 1) {
				host = proLiantDL380G5QuadCore.build();
			} else {
				host = proLiantDL160G5E5420.build();
			}
			
			//power hosts on by default TODO change
			host.setState(Host.HostState.ON);

			AutonomicManager hostAM = new AutonomicManager(simulation, new HostManager(host), new HostManagerBroadcast(host, broadcastingGroup));
			hostAM.installPolicy(new HostOperationsPolicy());
			
			long monitorStart = simulation.getRandom().nextInt(10) * SimTime.seconds(30); //generate a random start time up to 5 minutes for monitors, with a resolution of 30 seconds for event update performance
//			long monitorStart = simulation.getRandom().nextInt((int)(SimTime.minutes(5)));
			hostAM.installPolicy(new HostMonitoringPolicyBroadcast(lower, upper, target), SimTime.minutes(5), monitorStart);

			broadcastingGroup.addMember(hostAM);
			
			host.installAutonomicManager(hostAM);
			
			dataCentre.addHost(host);
			hostPool.addHost(host, hostAM);
		}
	
	}
	
	public static DistributedMetrics getDistributedMetrics(Simulation simulation) {
		DistributedMetrics metrics = simulation.getSimulationMetrics().getCustomMetricCollection(DistributedMetrics.class);
		
		if (metrics == null) {
			metrics = new DistributedMetrics(simulation);
			simulation.getSimulationMetrics().addCustomMetricCollection(metrics);
			return metrics;
		} else {
			return (DistributedMetrics)metrics;
		}
	}
	
	/**
	 * Creates a Service Producer to spawn new services over time and thus 
	 * populate the data centre. The services respond to the single-tier 
	 * interactive service model.
	 */
	public static void configureStaticServices(Simulation simulation, AutonomicManager dcAM) {
		// Create a service rate _trace_ for the ServiceProducer.
		ArrayList<Tuple<Long, Double>> serviceRates = new ArrayList<Tuple<Long, Double>>();
		serviceRates.add(new Tuple<Long, Double>(SimTime.seconds(1), 10d));		// Create ~400 VMs.
		serviceRates.add(new Tuple<Long, Double>(SimTime.days(1), 0d));		// Create ~400 VMs.
//		serviceRates.add(new Tuple<Long, Double>(1000l, 30d));		// Create ~1200 VMs.
//		serviceRates.add(new Tuple<Long, Double>(1000l, 40d));		// Create ~1600 VMs.
		serviceRates.add(new Tuple<Long, Double>(144000000l, 0d));	// 40 hours
		serviceRates.add(new Tuple<Long, Double>(864000000l, 0d));	// 10 days
		
		ApplicationGeneratorLegacy serviceProducer = new IMServiceProducer(simulation, dcAM, null, serviceRates);
		serviceProducer.start();
	}
	
	/**
	 * Creates Service Producers to spawn services over time in such a manner as to dynamically
	 * vary the number of services within the simulation over time, according to a fixed plan
	 * @param simulation
	 * @param dc
	 */
	public static void configureDynamicServices(Simulation simulation, AutonomicManager dcAM) {
		
		/*
		 * 1. Create 600 Services (VMs) over first 40 hours. These Services to not terminate.
		 * 2. Simulation recording starts after 2 days
		 * 3. Hold on 600 Services for day 3
		 * 4. Increase from 600 to 1200 throughout day 4
		 * 5. Hold on 1200 for day 5
		 * 6. Decrease from 1200 to 800 throughout day 6
		 * 7. Hold on 800 for day 7
		 * 8. Increase from 800 to 1600 throughout day 8
		 * 9. Hold on 1600 for day 9
		 * 10. Decrease from 1600 to 600 for day 10
		 * 11. Complete 8 days of recorded simulation
		 */
		
		/*
		 * Configure and start the base 600 services which do not terminate
		 */
		ArrayList<Tuple<Long, Double>> serviceRates = new ArrayList<Tuple<Long, Double>>();
		serviceRates.add(new Tuple<Long, Double>(SimTime.seconds(1), 15d));		// Create ~600 VMs.
		serviceRates.add(new Tuple<Long, Double>(SimTime.hours(40), 0d));		// over 40 hours
		serviceRates.add(new Tuple<Long, Double>(SimTime.days(10), 0d));		// 10 days
		
		ApplicationGeneratorLegacy serviceProducer = new IMServiceProducer(simulation, dcAM, null, serviceRates);
		serviceProducer.start();

		/*
		 * Create time varying service levels. Each service has a lifespan of ~2 days, normally distributed with a std. dev. of 2 hours
		 */
		serviceRates = new ArrayList<Tuple<Long, Double>>();
		
		//Day 4: Create 600 new services throughout the day
		serviceRates.add(new Tuple<Long, Double>(SimTime.days(3), 25d));
		
		//Day 5: Hold at 1200
		serviceRates.add(new Tuple<Long, Double>(SimTime.days(4), 0d));

		//Day 6: Create 200 new services, which combined with the termination of previous will bring us down to 800
		serviceRates.add(new Tuple<Long, Double>(SimTime.days(5), 8.3d));
		
		//Day 7: Hold at 800
		serviceRates.add(new Tuple<Long, Double>(SimTime.days(6), 0d));
		
		//Day 8: Create 1000 new services throughout the day
		serviceRates.add(new Tuple<Long, Double>(SimTime.days(7), 41.6d));
		
		//Day 9: Hold at 1600
		serviceRates.add(new Tuple<Long, Double>(SimTime.days(8), 0d));
		
		//Day 10: Let servers terminate until left with base 600
		serviceRates.add(new Tuple<Long, Double>(SimTime.days(10), 0d));
		
		serviceProducer = new IMServiceProducer(simulation, dcAM, new NormalDistribution(SimTime.days(2), SimTime.hours(2)), serviceRates);
		serviceProducer.start();	
	}
	
	/**Ok, let me try to clarify a bit the way applications are modeled at the moment. It should hopefully address both this email and the email sent directly to Gaston and I. Take a look through, and feel free to pass it on (with Gaston and I copied).

Hosts (physical machines) in the data centre run Virtual Machines (VMs). Each VM runs a single Application instance, which may or may not be a part of a larger Service or Service Tier. We define a Service Tier as a set of identical Application instances, running each within their own VM, which split incoming workload via a Load Balancer element. A Service is defined as an ordered set of Service Tiers, whereby the amount of work being completed by one tier is used as the input level for the next.

Now, let's discuss exactly what the Applications and their workload is. At the moment, an Application is a continuous, transactional application such as a web server. It does not have a defined amount of work that it must complete before terminating like a batch-style or HPC workload would (which is what CloudSim models). Rather, a Workload element defines the "level" of incoming work at any given time in the simulation, which you can think of as something akin to "incoming requests per second". The Application uses this incoming workload level to calculate the amount of CPU resource it requires to process it. This is done simply my multiplying the incoming workload by a specified "CPU per work unit" value, which typically we just set to "1". CPU is represented as a value equal to the clock speed of the processor in MHz, so a 2GHz processor has "2000" CPU units available to be divided among the VMs running on the host. So, if the workload of an application is current 950, then the VM running the application requires 950 CPU units, with the option of adding an additional static overhead value to represent the basic idle CPU usage of the VM.

Of course, the VM may not get all of the CPU it requires in order to meet the demand of the incoming workload level. This happens in the case that the host is overloaded and there is CPU contention. If the VM does not get the CPU it requires, then we calculate how much "work" it can complete given the resources it DOES get (using the same "CPU per work unit" value), and the record the difference multiplied by the number of seconds the VM is underprovisioned as an SLA violation. You can think of this as the number of incoming requests that were dropped due to lack of resources. In the case that the output of the Application is being fed into another Service Tier, only the actual work level being completed is used as the input for the next tier.

We are also working on other metrics related to our SLA violation, such as the duration of SLA violation, and the severity.

To determine the changes in incoming workload levels, we have been using a few freely available web server traces. We divide the trace into equal length segments (i.e. 120 second intervals), and total the number of requests in each interval. We then normalize the values of each interval to [0, 1], with 0 being zero requests, and 1 being the maximum number of requests received in any interval. We then scale these normalized traces to match the VM sizes we create in the simulator. So, for example, if we want a VM that uses 1 core at 2GHz, we scale the normalized traces by 2000 (remember 2GHz = 2000 CPU units).

Currently, we do not have an Application model that has a specified amount of computation to complete before termination. Applications can be set to terminate at a certain time in the simulation, representing the client shutting down the VM, but as of now there is no work with completion time based on processing a specified number of "CPU units", as in CloudSim. Now, that is not to say that it would be difficult to implement. On the contrary, I am fairly certain that it wouldn't present much of a challenge for me to implement this application type. An application class could be built that has a specified number of CPU units it must consume before completion, and terminates when it has done so. It's could be designed to simply use as much CPU as it can get (up to the limit imposed by the number of virtual cores it's VM is assigned), to use up to a specified limit, or perhaps to follow some sort of utilization trace.

Now, let's look at how contention is handled. CPU is given fairly to each VM on a host (fair-share), and any remaining CPU requirement that any of the VMs have is left unfulfilled, which has the aforementioned consequences.
	 * Configure services to arrival such that the overall utilization of the datacentre changes randomly.
	 * @param simulation
	 * @param dc
	 * @param changesPerDay The number of utilization changes (arrival rate changes) per day
	 * @param minServices The minimum number of services running in the data centre
	 * @param maxServices The maximum number of services running in the data centre
	 */
	public static void configureRandomServices(Simulation simulation, AutonomicManager dcAM, double changesPerDay, int minServices, int maxServices) {

		/*
		 * Configure minimum service level. Create the minimum number of services over the first 40 hours,
		 * and leave them running for the entire simulation.
		 */
		ArrayList<Tuple<Long, Double>> serviceRates = new ArrayList<Tuple<Long, Double>>();
		serviceRates.add(new Tuple<Long, Double>(SimTime.seconds(1), (minServices / 40d)));		
		serviceRates.add(new Tuple<Long, Double>(SimTime.hours(40), 0d));		
		serviceRates.add(new Tuple<Long, Double>(SimTime.days(10), 0d));		// 10 days
		
		ApplicationGeneratorLegacy serviceProducer = new IMServiceProducer(simulation, dcAM, null, serviceRates);
		serviceProducer.start();
		
		//Create a uniform random distribution to generate the number of services within the data centre.
		UniformIntegerDistribution serviceCountDist = new UniformIntegerDistribution(0, (maxServices - minServices));
		serviceCountDist.reseedRandomGenerator(simulation.getRandom().nextLong());
		
		/*
		 * Generate the service arrival rates for the rest of the simulation
		 */
		long time;		//start time of the current arrival rate
		long nextTime;	//the time of the next arrival rate change
		double rate;	//the current arrival rate
		serviceRates = new ArrayList<Tuple<Long, Double>>(); //list of arrival rates
		
		time = SimTime.days(2); //start at beginning of 3rd day (end of 2nd)
		
		//loop while we still have simulation time to generate arrival rates for
		while (time < SimTime.days(10)) {

			//calculate the next time the rate will changes
			nextTime = time + Math.round(SimTime.days(1) / changesPerDay);
			
			//generate a target VM count to reach by the next rate change
			double target = serviceCountDist.sample();
			
			//caculate the current arrival rate necessary to reach the target VM count
			rate = target / ((nextTime - time) / 1000d / 60d / 60d);
			
			//add the current rate to the list of arrival rates
			serviceRates.add(new Tuple<Long, Double>(time, rate));
			
			//advance to the next time intrerval
			time = nextTime;
		}
		//add a final rate of 0 to run until the end of the simulation
		serviceRates.add(new Tuple<Long, Double>(SimTime.days(10), 0d));

		
		serviceProducer = new IMServiceProducer(simulation, dcAM, new NormalDistribution(SimTime.days(1) / changesPerDay, SimTime.hours(1)), serviceRates);
		serviceProducer.start();
	}
		
	/**
	 * Creates services for the IM 2013 Test Environment.
	 * 
	 * @author Michael Tighe
	 *
	 */
	public static class IMServiceProducer extends ApplicationGeneratorLegacy {

		private int counter = 0;
		
		public IMServiceProducer(Simulation simulation, AutonomicManager dcTarget, RealDistribution lifespanDist, List<Tuple<Long, Double>> servicesPerHour) {
			super(simulation, dcTarget, lifespanDist, servicesPerHour);
		}

		@Override
		public Application buildApplication() {
			++counter;
		
			String trace = TRACES[counter % N_TRACES];
			long offset = (int)(simulation.getRandom().nextDouble() * OFFSET_MAX[counter % N_TRACES]);
			
			int cores = VM_CORES[counter % N_VM_SIZES];
			int coreCapacity = VM_SIZES[counter % N_VM_SIZES];
			int memory = VM_RAM[counter % N_VM_SIZES];
			int bandwidth = 12800;	// 100 Mb/s
			int storage = 1024;	// 1 GB
			
			// Create workload (external) for the service.
			TraceWorkload workload = new TraceWorkload(simulation, trace, offset); //scale to n replicas
			
			InteractiveApplication application = Applications.singleTaskInteractiveApplication(simulation, workload, cores, coreCapacity, memory, bandwidth, storage, 0.01);
			workload.setScaleFactor(application.calculateMaxWorkloadUtilizationLimit(0.98));
			
			return application;
		}
		
	}

}
