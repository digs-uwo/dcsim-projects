package edu.uwo.csd.dcsim.extras.experiments;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.application.*;
import edu.uwo.csd.dcsim.application.workload.*;
import edu.uwo.csd.dcsim.common.Tuple;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.metrics.Metric;
import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.host.resourcemanager.*;
import edu.uwo.csd.dcsim.host.scheduler.FairShareCpuSchedulerFactory;
import edu.uwo.csd.dcsim.management.*;

/**
 * This class serves to create a common virtualized data centre environment in 
 * which to run our experiments for IM 2012.
 * 
 * @author Gaston Keller
 *
 */
public class IM2012TestEnvironment {

	// The data centre is intended to be organized in the future in 5 clusters 
	// of 10 racks each, with each rack containing 40 servers, for a total 
	// count of 2000 servers.
	public static final int N_HOSTS = 200; // 2000
	//public static final int N_VMS = 600; // 20000
	
	public static final int CPU_OVERHEAD = 200;
	public static final int[] VM_SIZES = {1500, 2500, 3000, 3000};
	public static final int[] VM_CORES = {1, 1, 1, 2};
	public static final int[] VM_RAM = {512, 1024, 1024, 1024};
	public static final int N_VM_SIZES = 4;
	
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
	
	private static Logger logger = Logger.getLogger(IM2012TestEnvironment.class);
	
	/**
	 * Creates a DataCentre object, which contains a collection of hosts, and 
	 * uses the VMPlacementPolicyFFD by default.
	 * 
	 * This method also instantiates the ServiceProducer.
	 * 
	 * @param simulation
	 * @return		the built data centre
	 */
	public static DataCentre createDataCentre(DataCentreSimulation simulation) {
		// Create data centre with default VM Placement policy.
		DataCentre dc = new DataCentre(new VMPlacementPolicyFFD(simulation));
		
		// Create hosts and add to data centre.
		dc.addHosts(createHosts(simulation));
		
		return dc;
	}
	
	/**
	 * Creates the collection of hosts for the data centre. The hosts are 
	 * equally divided between the two available models: proLiantDL360G5E5450 
	 * and proLiantDL160G5E5420.
	 * 
	 * @param simulation
	 * @return		the collection of hosts
	 */
	private static ArrayList<Host> createHosts(Simulation simulation) {
		ArrayList<Host> hosts = new ArrayList<Host>();
		
		for (int i = 0; i < N_HOSTS; ++i) {
			Host host;
			
			Host.Builder proLiantDL360G5E5450 = HostModels.ProLiantDL360G5E5450(simulation).privCpu(500).privBandwidth(131072)
					.cpuManagerFactory(new OversubscribingCpuManagerFactory())
					.memoryManagerFactory(new SimpleMemoryManagerFactory())
					.bandwidthManagerFactory(new SimpleBandwidthManagerFactory())
					.storageManagerFactory(new SimpleStorageManagerFactory())
					.cpuSchedulerFactory(new FairShareCpuSchedulerFactory(simulation));
			
			Host.Builder proLiantDL160G5E5420 = HostModels.ProLiantDL160G5E5420(simulation).privCpu(500).privBandwidth(131072)
					.cpuManagerFactory(new OversubscribingCpuManagerFactory())
					.memoryManagerFactory(new SimpleMemoryManagerFactory())
					.bandwidthManagerFactory(new SimpleBandwidthManagerFactory())
					.storageManagerFactory(new SimpleStorageManagerFactory())
					.cpuSchedulerFactory(new FairShareCpuSchedulerFactory(simulation));
			
			if (i % 2 == 1) {
				host = proLiantDL360G5E5450.build();
			} else {
				host = proLiantDL160G5E5420.build();
			}
			
			hosts.add(host);
		}
		
		return hosts;
	}
	
	/**
	 * Creates a Service Producer to spawn new services over time and thus 
	 * populate the data centre. The services respond to the single-tier 
	 * interactive service model.
	 */
	public static ServiceProducer createServiceProducer(DataCentreSimulation simulation, DataCentre dc) {
		// Create a service rate _trace_ for the ServiceProducer.
		ArrayList<Tuple<Long, Double>> serviceRates = new ArrayList<Tuple<Long, Double>>();
		//serviceRates.add(new Tuple<Long, Double>(1000l, 10d));
		//serviceRates.add(new Tuple<Long, Double>(14400000l, 30d));
		//serviceRates.add(new Tuple<Long, Double>(28800000l, 5d));
		//serviceRates.add(new Tuple<Long, Double>(43200000l, 5d));
		serviceRates.add(new Tuple<Long, Double>(1000l, 100d));
		serviceRates.add(new Tuple<Long, Double>(72000000l, 0d));	// 10 hours
		serviceRates.add(new Tuple<Long, Double>(864000000l, 0d));	// 10 days
		
		// Create the ServiceProducer.
		ServiceProducer serviceProducer = new ServiceProducer(simulation, dc, new NormalDistribution(1000000000, 1), serviceRates) {
			
			private int counter = 0;
			
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
				
				return Services.singleTierInteractiveService(workload, cores, coreCapacity, memory, bandwidth, storage, 1, 0, CPU_OVERHEAD, 1, Integer.MAX_VALUE);
			}
			
		};
		
		return serviceProducer;
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
	
}
