package edu.uwo.csd.dcsim.projects.applicationManagement;

import java.util.Random;

import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.application.loadbalancer.LoadBalancer;
import edu.uwo.csd.dcsim.application.loadbalancer.ShareLoadBalancer;
import edu.uwo.csd.dcsim.application.sla.InteractiveServiceLevelAgreement;
import edu.uwo.csd.dcsim.application.workload.TraceWorkload;
import edu.uwo.csd.dcsim.common.ObjectBuilder;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.host.Cluster;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.HostModels;
import edu.uwo.csd.dcsim.host.Rack;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.host.SwitchFactory;
import edu.uwo.csd.dcsim.host.resourcemanager.DefaultResourceManagerFactory;
import edu.uwo.csd.dcsim.host.scheduler.DefaultResourceSchedulerFactory;
import edu.uwo.csd.dcsim.management.AutonomicManager;

public abstract class AppManagementTestEnvironment {

	public static final int MAX_APP_SCALE = 5;
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
	
	int hostsPerRack;
	int nRacks;
	int nApps = 0;
	Simulation simulation;
	AutonomicManager dcAM;
	ObjectBuilder<LoadBalancer> loadBalancerBuilder;
	Random envRandom;
	Random appGenerationRandom;
		
	public AppManagementTestEnvironment(Simulation simulation, int hostsPerRack, int nRacks, ObjectBuilder<LoadBalancer> loadBalancerBuilder) {
		this.simulation = simulation;
		this.hostsPerRack = hostsPerRack;
		this.nRacks = nRacks;
		
		envRandom = new Random(simulation.getRandom().nextLong());
		appGenerationRandom = new Random(simulation.getRandom().nextLong());
		this.loadBalancerBuilder = loadBalancerBuilder;

	}
	
	public DataCentre createDataCentre(Simulation simulation) {
		// Create data centre.
		
		//Define Hosts
		Host.Builder proLiantDL160G5E5420 = HostModels.ProLiantDL160G5E5420(simulation).privCpu(500).privBandwidth(131072)
				.resourceManagerFactory(new DefaultResourceManagerFactory())
				.resourceSchedulerFactory(new DefaultResourceSchedulerFactory());
		
		//Define Racks
		SwitchFactory switch10g48p = new SwitchFactory(10000000, 48, 100);
		
		Rack.Builder seriesA = new Rack.Builder(simulation).nSlots(40).nHosts(hostsPerRack)
				.hostBuilder(proLiantDL160G5E5420)
				.switchFactory(switch10g48p);
		
		// Define Cluster
		SwitchFactory switch40g24p = new SwitchFactory(40000000, 24, 100);
		
		Cluster.Builder series09 = new Cluster.Builder(simulation).nRacks(nRacks).nSwitches(1)
				.rackBuilder(seriesA)
				.switchFactory(switch40g24p);
		
		
		DataCentre dc = new DataCentre(simulation, switch40g24p);
		simulation.addDatacentre(dc);
		
		dcAM = new AutonomicManager(simulation);

		processDcAM(dcAM);
		
		//add single cluster
		dc.addCluster(series09.build());
		
		for (Cluster cluster : dc.getClusters()) {
			for (Rack rack : cluster.getRacks()) {
				for (Host host : rack.getHosts()) {
					processHost(host, rack, cluster, dc, dcAM);
				}
			}
		}
	
		return dc;
	}
	
	public abstract void processDcAM(AutonomicManager dcAM);
	
	public abstract void processHost(Host host, Rack rack, Cluster cluster, DataCentre dc, AutonomicManager dcAM);
	
	public Application createApplication() {
		return createApplication(appGenerationRandom.nextInt(5), appGenerationRandom.nextInt(MAX_APP_SCALE) + 1, false);
	}
	
	public Application createApplication(boolean fullSize) {
		return createApplication(appGenerationRandom.nextInt(5), appGenerationRandom.nextInt(MAX_APP_SCALE) + 1, fullSize);
	}
	
	public Application createApplication(int appTemplate, int appScale, boolean fullSize) {
		++nApps;
		
		int trace = appGenerationRandom.nextInt(N_TRACES);		
		TraceWorkload workload = new TraceWorkload(simulation, 
				TRACES[trace], 
				(long)(appGenerationRandom.nextDouble() * OFFSET_MAX[trace]));
		
		InteractiveApplication.Builder appBuilder;
		
		switch(appTemplate) {
		case 0:
			if (!fullSize) {
				appBuilder = new InteractiveApplication.Builder(simulation).thinkTime(4)
						.task(1, appScale, new Resources(2500,1024,0,0), 0.005, 1, loadBalancerBuilder)
						.task(4, 4 * appScale, new Resources(2500,1024,0,0), 0.02, 1, loadBalancerBuilder)
						.task(2, 2 * appScale, new Resources(2500,1024,0,0), 0.01, 1, loadBalancerBuilder);
			} else {
				appBuilder = new InteractiveApplication.Builder(simulation).thinkTime(4)
						.task(appScale, appScale, new Resources(2500,1024,0,0), 0.005, 1, loadBalancerBuilder)
						.task(4 * appScale, 4 * appScale, new Resources(2500,1024,0,0), 0.02, 1, loadBalancerBuilder)
						.task(2 * appScale, 2 * appScale, new Resources(2500,1024,0,0), 0.01, 1, loadBalancerBuilder);
			}
			break;
		case 1:
			if (!fullSize) {
				appBuilder = new InteractiveApplication.Builder(simulation).thinkTime(4)
						.task(1, appScale, new Resources(2500,1024,0,0), 0.005, 1, loadBalancerBuilder)
						.task(4, 4 * appScale, new Resources(2500,1024,0,0), 0.02, 1, loadBalancerBuilder);
			} else {
				appBuilder = new InteractiveApplication.Builder(simulation).thinkTime(4)
						.task(appScale, appScale, new Resources(2500,1024,0,0), 0.005, 1, loadBalancerBuilder)
						.task(4 * appScale, 4 * appScale, new Resources(2500,1024,0,0), 0.02, 1, loadBalancerBuilder);
			}
			break;
		case 2:
			if (!fullSize) {
				appBuilder = new InteractiveApplication.Builder(simulation).thinkTime(4)
						.task(1, appScale, new Resources(2500,1024,0,0), 0.005, 1, loadBalancerBuilder)
						.task(4, 4 * appScale, new Resources(2500,1024,0,0), 0.02, 1, loadBalancerBuilder)
						.task(2, 2 * appScale, new Resources(2500,1024,0,0), 0.01, 1, loadBalancerBuilder)
						.task(1, 1 * appScale, new Resources(2500,1024,0,0), 0.01, 0.5, loadBalancerBuilder)
						.task(2, 2 * appScale, new Resources(2500,1024,0,0), 0.02, 0.5, loadBalancerBuilder);
			} else {
				appBuilder = new InteractiveApplication.Builder(simulation).thinkTime(4)
						.task(appScale, appScale, new Resources(2500,1024,0,0), 0.005, 1, loadBalancerBuilder)
						.task(4 * appScale, 4 * appScale, new Resources(2500,1024,0,0), 0.02, 1, loadBalancerBuilder)
						.task(2 * appScale, 2 * appScale, new Resources(2500,1024,0,0), 0.01, 1, loadBalancerBuilder)
						.task(1 * appScale, 1 * appScale, new Resources(2500,1024,0,0), 0.01, 0.5, loadBalancerBuilder)
						.task(2 * appScale, 2 * appScale, new Resources(2500,1024,0,0), 0.02, 0.5, loadBalancerBuilder);
			}
			break;
		case 3:
			if (fullSize) {
				appBuilder = new InteractiveApplication.Builder(simulation).thinkTime(4)
						.task(1, appScale, new Resources(2500,1024,0,0), 0.01, 1, loadBalancerBuilder);
			} else {
				appBuilder = new InteractiveApplication.Builder(simulation).thinkTime(4)
						.task(appScale, appScale, new Resources(2500,1024,0,0), 0.01, 1, loadBalancerBuilder);
			}
			break;
		default: //case 4
			appBuilder = new InteractiveApplication.Builder(simulation).thinkTime(4)
			.task(1, 1, new Resources(2500,1024,0,0), 0.01, 1, loadBalancerBuilder);
		}
		
		InteractiveApplication app = appBuilder.build();
		app.setWorkload(workload);
		
		workload.setScaleFactor(app.calculateMaxWorkloadResponseTimeLimit(1)); //1s response time SLA

		InteractiveServiceLevelAgreement sla = new InteractiveServiceLevelAgreement(app).responseTime(1, 1); //sla limit at 1s response time, penalty rate of 1 per second in violation
		app.setSla(sla);
		
		processApplication(app);
		
		return app;
	}
	
	public abstract void processApplication(InteractiveApplication application);
	
	public AutonomicManager getDcAM() {
		return dcAM;
	}
	
}
