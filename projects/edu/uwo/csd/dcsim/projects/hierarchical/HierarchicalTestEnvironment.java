package edu.uwo.csd.dcsim.projects.hierarchical;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.common.Tuple;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Cluster;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.HostModels;
import edu.uwo.csd.dcsim.host.Rack;
import edu.uwo.csd.dcsim.host.SwitchFactory;
import edu.uwo.csd.dcsim.host.resourcemanager.DefaultResourceManagerFactory;
import edu.uwo.csd.dcsim.host.scheduler.DefaultResourceSchedulerFactory;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.capabilities.HostManager;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.management.policies.HostMonitoringPolicy;
import edu.uwo.csd.dcsim.management.policies.HostOperationsPolicy;
import edu.uwo.csd.dcsim.projects.centralized.policies.ReactiveHostStatusPolicy;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.*;
import edu.uwo.csd.dcsim.projects.hierarchical.policies.*;

/**
 * This class serves to create a common virtualized data centre environment in 
 * which to run experiments for NOMS 2014.
 * 
 * @author Gaston Keller
 *
 */
public class HierarchicalTestEnvironment {

	public static final int N_CLUSTERS = 5;
	public static final int N_RACKS = 10;
	public static final int N_HOSTS = 40;
	
	public static final int CPU_OVERHEAD = 200;
	public static final int[] VM_SIZES = {1500, 2500, 2500};
	public static final int[] VM_CORES = {1, 1, 2};
	public static final int[] VM_RAM = {512, 1024, 1024};
	public static final int N_VM_SIZES = 3;
//	public static final int[] VM_SIZES = {1500, 2500, 3000, 3000};
//	public static final int[] VM_CORES = {1, 1, 1, 2};
//	public static final int[] VM_RAM = {512, 1024, 1024, 1024};
//	public static final int N_VM_SIZES = 4;
	
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
	
	private static Logger logger = Logger.getLogger(HierarchicalTestEnvironment.class);
	
	public HierarchicalTestEnvironment() {
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * Creates a DataCentre object and its corresponding AutonomicManager object. The data centre 
	 * is organized in Clusters, which consist of Racks, which in turn consist of Hosts.
	 * 
	 * Each entity is paired with its corresponding autonomic manager and policies.
	 * 
	 * This method also instantiates the ServiceProducer.
	 */
	public static Tuple<DataCentre, AutonomicManager> createEnvironment(Simulation simulation) {
		// Create data centre.
		DataCentre dc = createInfrastructure(simulation);
		simulation.addDatacentre(dc);
		
		return new Tuple<DataCentre, AutonomicManager>(dc, createMgmtInfrastructure(simulation, dc));
	}
	
	public static DataCentre createInfrastructure(Simulation simulation) {
		// Define Switch types.
		SwitchFactory switch10g48p = new SwitchFactory(10000000, 48, 100);
		SwitchFactory switch40g24p = new SwitchFactory(40000000, 24, 100);
		
		// Define Host types.
		Host.Builder proLiantDL380G5QuadCore = HostModels.ProLiantDL380G5QuadCore(simulation).privCpu(500).privBandwidth(131072)
				.resourceManagerFactory(new DefaultResourceManagerFactory())
				.resourceSchedulerFactory(new DefaultResourceSchedulerFactory());
		
		Host.Builder proLiantDL160G5E5420 = HostModels.ProLiantDL160G5E5420(simulation).privCpu(500).privBandwidth(131072)
				.resourceManagerFactory(new DefaultResourceManagerFactory())
				.resourceSchedulerFactory(new DefaultResourceSchedulerFactory());
		
		// Define Rack types.
		Rack.Builder seriesA = new Rack.Builder(simulation).nSlots(40).nHosts(N_HOSTS)
				.hostBuilder(proLiantDL380G5QuadCore)
				.switchFactory(switch10g48p);
		
		Rack.Builder seriesB = new Rack.Builder(simulation).nSlots(40).nHosts(N_HOSTS)
				.hostBuilder(proLiantDL160G5E5420)
				.switchFactory(switch10g48p);
		
		// Define Cluster types.
		Cluster.Builder series09 = new Cluster.Builder(simulation).nRacks(N_RACKS).nSwitches(1)
				.rackBuilder(seriesA)
				.switchFactory(switch40g24p);
		
		Cluster.Builder series11 = new Cluster.Builder(simulation).nRacks(N_RACKS).nSwitches(1)
				.rackBuilder(seriesB)
				.switchFactory(switch40g24p);
		
		// Create data centre.
		DataCentre dc = new DataCentre(simulation, switch40g24p);
		simulation.addDatacentre(dc);
		
		// Create clusters in data centre.
		for (int i = 0; i < N_CLUSTERS; i++) {
			if (i % 2 == 0)
				dc.addCluster(series09.build());
			else
				dc.addCluster(series11.build());
		}
		
		return dc;
	}
	
	public static AutonomicManager createMgmtInfrastructure(Simulation simulation, DataCentre dc) {
		
		// Create DC Manager.
		ClusterPoolManager clusterPool = new ClusterPoolManager();
		AutonomicManager dcManager = new AutonomicManager(simulation, clusterPool, new MigRequestRecord());
		
		dcManager.installPolicy(new ClusterStatusPolicy(5));
		dcManager.installPolicy(new VmPlacementPolicyLevel2());
		dcManager.installPolicy(new VmRelocationPolicyLevel2());
		
		for (Cluster cluster : dc.getClusters()) {
			// Create Cluster's autonomic manager.
			RackPoolManager rackPool = new RackPoolManager();
			AutonomicManager clusterManager = new AutonomicManager(simulation, new ClusterManager(cluster), rackPool, new MigRequestRecord());
			
			// Install management policies in the autonomic manager.
			clusterManager.installPolicy(new ClusterMonitoringPolicy(dcManager), SimTime.minutes(5), SimTime.minutes(simulation.getRandom().nextInt(5)));
			clusterManager.installPolicy(new RackStatusPolicy(5));
			clusterManager.installPolicy(new VmPlacementPolicyLevel1(dcManager));
			clusterManager.installPolicy(new VmRelocationPolicyLevel1(dcManager));
			
			// Autonomic manager is NOT installed anywhere.
			
			// Add Cluster and its autonomic manager to the capability of the hosting Data Centre.
			clusterPool.addCluster(cluster, clusterManager);
			
			for (Rack rack : cluster.getRacks()) {
				// Create Rack's autonomic manager.
				HostPoolManager hostPool = new HostPoolManager();
				AutonomicManager rackManager = new AutonomicManager(simulation, new RackManager(rack), hostPool, new MigRequestRecord());
				
				// Install management policies in the autonomic manager.
				rackManager.installPolicy(new RackMonitoringPolicy(clusterManager), SimTime.minutes(5), SimTime.minutes(simulation.getRandom().nextInt(5)));
				rackManager.installPolicy(new ReactiveHostStatusPolicy(5));
				rackManager.installPolicy(new VmPlacementPolicyFFMHybrid(clusterManager, lower, upper, target));
				rackManager.installPolicy(new VmRelocationPolicyFFIMHybrid(clusterManager, lower, upper, target));
				rackManager.installPolicy(new VmConsolidationPolicyFFDDIHybrid(clusterManager, lower, upper, target));
				
				// Autonomic manager is NOT installed anywhere.
				
				// Add Rack and its autonomic manager to the capability of the hosting Cluster.
				rackPool.addRack(rack, rackManager);
				
				for (Host host : rack.getHosts()) {
					// Create Host's autonomic manager.
					AutonomicManager hostManager = new AutonomicManager(simulation, new HostManager(host));
					
					// Install management policies in the autonomic manager.
					hostManager.installPolicy(new HostMonitoringPolicy(rackManager), SimTime.minutes(5), SimTime.minutes(simulation.getRandom().nextInt(5)));
					hostManager.installPolicy(new HostOperationsPolicy());
					
					// Install autonomic manager in the Host.
					host.installAutonomicManager(hostManager);
					
					// Add Host and its autonomic manager to the capability of the hosting Rack.
					hostPool.addHost(host, hostManager);
				}
			}
			
		}
		
		return dcManager;
	}

}
