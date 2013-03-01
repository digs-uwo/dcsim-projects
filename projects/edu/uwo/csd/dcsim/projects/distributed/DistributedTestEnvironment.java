package edu.uwo.csd.dcsim.projects.distributed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.application.Service;
import edu.uwo.csd.dcsim.application.ServiceProducer;
import edu.uwo.csd.dcsim.application.Services;
import edu.uwo.csd.dcsim.application.workload.TraceWorkload;
import edu.uwo.csd.dcsim.application.workload.Workload;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.common.Tuple;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.metrics.Metric;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.HostModels;
import edu.uwo.csd.dcsim.host.resourcemanager.DefaultResourceManagerFactory;
import edu.uwo.csd.dcsim.host.scheduler.DefaultResourceSchedulerFactory;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.capabilities.HostManager;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.management.policies.HostMonitoringPolicy;
import edu.uwo.csd.dcsim.management.policies.HostOperationsPolicy;
import edu.uwo.csd.dcsim.management.policies.HostStatusPolicy;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.HostManagerBroadcast;
import edu.uwo.csd.dcsim.projects.distributed.policies.HostMonitoringPolicyBroadcast;
import edu.uwo.csd.dcsim.projects.im2013.IM2013TestEnvironment;

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
	
	private static Logger logger = Logger.getLogger(IM2013TestEnvironment.class);
	
	/**
	 * Creates a DataCentre object and its corresponding AutonomicManager 
	 * object. The data centre consists of a collection of hosts.
	 * 
	 * This method also instantiates the ServiceProducer.
	 * 
	 * @param simulation
	 * @return		the built data centre
	 */
	public static Tuple<DataCentre, AutonomicManager> createDataCentre(Simulation simulation) {
		// Create data centre.
		DataCentre dc = new DataCentre(simulation);
		simulation.addDatacentre(dc);
		
		HostPoolManager hostPool = new HostPoolManager();
		AutonomicManager dcAM = new AutonomicManager(simulation, hostPool);
		dcAM.installPolicy(new HostStatusPolicy(5));
		
		// Create hosts and add to data centre.
		createHosts(simulation, dc, dcAM);
		
		return new Tuple<DataCentre, AutonomicManager>(dc, dcAM);
	}
	
	/**
	 * Creates the collection of hosts for the data centre. The hosts are 
	 * equally divided between the two available models: proLiantDL360G5E5450 
	 * and proLiantDL160G5E5420.
	 */
	private static void createHosts(Simulation simulation, DataCentre dataCentre, AutonomicManager dcAM) {
		
		HostPoolManager hostPool = dcAM.getCapability(HostPoolManager.class);
		
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
			
			AutonomicManager hostAM = new AutonomicManager(simulation, new HostManagerBroadcast(host));
			hostAM.installPolicy(new HostOperationsPolicy());
			hostAM.installPolicy(new HostMonitoringPolicyBroadcast(0.5, 0.9, 0.85), SimTime.minutes(5), 0);
			
//			AutonomicManager hostAM = new AutonomicManager(simulation, new HostManager(host));
//			hostAM.installPolicy(new HostMonitoringPolicy(dcAM), SimTime.minutes(5), SimTime.minutes(simulation.getRandom().nextInt(5)));
//			hostAM.installPolicy(new HostOperationsPolicy());

			host.installAutonomicManager(hostAM);
			
			dataCentre.addHost(host);
			hostPool.addHost(host, hostAM); //this is the host pool used by the data centre manager
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
		serviceRates.add(new Tuple<Long, Double>(1000l, 10d));		// Create ~400 VMs.
//		serviceRates.add(new Tuple<Long, Double>(1000l, 30d));		// Create ~1200 VMs.
//		serviceRates.add(new Tuple<Long, Double>(1000l, 40d));		// Create ~1600 VMs.
		serviceRates.add(new Tuple<Long, Double>(144000000l, 0d));	// 40 hours
		serviceRates.add(new Tuple<Long, Double>(864000000l, 0d));	// 10 days
		
		ServiceProducer serviceProducer = new IMServiceProducer(simulation, dcAM, null, serviceRates);
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
		
		ServiceProducer serviceProducer = new IMServiceProducer(simulation, dcAM, null, serviceRates);
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
	
	/**
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
		
		ServiceProducer serviceProducer = new IMServiceProducer(simulation, dcAM, null, serviceRates);
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
	 * Formats a simulation run results for output.
	 * 
	 * @param metrics	simulation run results
	 */
	public static void printMetrics(Collection<Metric> metrics) {
		for (Metric metric : metrics) {
			logger.info(metric.getName() +
					" = " +
					metric.toString());
		}
	}
	
	/**
	 * Creates services for the IM 2013 Test Environment.
	 * 
	 * @author Michael Tighe
	 *
	 */
	public static class IMServiceProducer extends ServiceProducer {

		private int counter = 0;
		
		public IMServiceProducer(Simulation simulation, AutonomicManager dcTarget, RealDistribution lifespanDist, List<Tuple<Long, Double>> servicesPerHour) {
			super(simulation, dcTarget, lifespanDist, servicesPerHour);
		}

		@Override
		public Service buildService() {
			++counter;
			
			String trace = TRACES[counter % N_TRACES];
			long offset = (int)(simulation.getRandom().nextDouble() * OFFSET_MAX[counter % N_TRACES]);
			
			int cores = VM_CORES[counter % N_VM_SIZES];
			int coreCapacity = VM_SIZES[counter % N_VM_SIZES];
			int memory = VM_RAM[counter % N_VM_SIZES];
			int bandwidth = 12800;	// 100 Mb/s
			long storage = 1024;	// 1 GB
			
			// Create workload (external) for the service.
			Workload workload = new TraceWorkload(simulation, trace, (coreCapacity * cores) - CPU_OVERHEAD, offset); //scale to n replicas
			simulation.addWorkload(workload);
			
			return Services.singleTierInteractiveService(workload, cores, coreCapacity, memory, bandwidth, storage, 1, CPU_OVERHEAD, 1, Integer.MAX_VALUE);
		}
		
	}

}
