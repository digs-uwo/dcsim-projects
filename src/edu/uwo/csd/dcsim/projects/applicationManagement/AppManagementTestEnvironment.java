package edu.uwo.csd.dcsim.projects.applicationManagement;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;

import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.application.ApplicationGenerator;
import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.application.loadbalancer.LoadBalancer;
import edu.uwo.csd.dcsim.application.sla.InteractiveServiceLevelAgreement;
import edu.uwo.csd.dcsim.application.workload.TraceWorkload;
import edu.uwo.csd.dcsim.common.ObjectBuilder;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.common.Tuple;
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
import edu.uwo.csd.dcsim.management.events.ApplicationPlacementEvent;

public abstract class AppManagementTestEnvironment {

	public static final long ARRIVAL_SYNC_INTERVAL = SimTime.minutes(1);
	
	public static final int N_APP_TEMPLATES = 4;
	public static final int MIN_APP_SCALE = 3;
	public static final int MAX_APP_SCALE = 6;
	public static final int N_TRACES = 3; 
	public static final String[] TRACES = {"traces/clarknet", 
		"traces/epa",
		"traces/sdsc",
		"traces/google_cores_job_type_0", 
		"traces/google_cores_job_type_1",
		"traces/google_cores_job_type_2",
		"traces/google_cores_job_type_3"};	
	public static final long[] OFFSET_MAX = {200000000, 40000000, 40000000, 15000000, 15000000, 15000000, 15000000};
	public static final double[] TRACE_AVG = {0.32, 0.25, 0.32, 0.72, 0.74, 0.77, 0.83};
	public static final long APP_RAMPUP_TIME = SimTime.hours(6);
	
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
		return createApplication(appGenerationRandom.nextInt(N_APP_TEMPLATES), appGenerationRandom.nextInt(MAX_APP_SCALE) + MIN_APP_SCALE, false);
	}
	
	public Application createApplication(boolean fullSize) {
		return createApplication(appGenerationRandom.nextInt(N_APP_TEMPLATES), appGenerationRandom.nextInt(MAX_APP_SCALE) + MIN_APP_SCALE, fullSize);
	}
	
	public Application createApplication(int appTemplate, int appScale, boolean fullSize) {
		++nApps;
		
		int trace = appGenerationRandom.nextInt(N_TRACES);		
		TraceWorkload workload = new TraceWorkload(simulation, 
				TRACES[trace], 
				(long)(appGenerationRandom.nextDouble() * OFFSET_MAX[trace]));
		
		workload.setRampUp(APP_RAMPUP_TIME);
		
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
			if (!fullSize) {
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
		
		workload.setScaleFactor(app.calculateMaxWorkloadResponseTimeLimit(0.9)); //1s response time SLA

		InteractiveServiceLevelAgreement sla = new InteractiveServiceLevelAgreement(app).responseTime(1, 1); //sla limit at 1s response time, penalty rate of 1 per second in violation
		app.setSla(sla);
		
		processApplication(app);
		
		return app;
	}
	
	public abstract void processApplication(InteractiveApplication application);
	
	public AutonomicManager getDcAM() {
		return dcAM;
	}
	
	
	/*
	 * APPLICATION GENERATION
	 * 
	 */
	
	public void configureStaticApplications(Simulation simulation, int nApps) {
		configureStaticApplications(simulation, nApps, false);
	}
	
	public void configureStaticApplications(Simulation simulation, int nApps, boolean fullSize) {
		ArrayList<Application> applications = new ArrayList<Application>();
		for (int i = 0; i < nApps; ++i) {
			applications.add(this.createApplication(fullSize));
		}
		simulation.sendEvent(new ApplicationPlacementEvent(dcAM, applications));
	}
	
	public void configureRandomApplications(Simulation simulation, double changesPerDay, int minServices, int maxServices, long rampUpTime, long startTime, long duration) {
		configureRandomApplications(simulation, changesPerDay, minServices, maxServices, rampUpTime, startTime, duration, false);
	}
	
	/**
	 * Configure applications to arrive such that the overall utilization of the datacentre changes randomly.
	 * @param simulation
	 * @param dc
	 * @param changesPerDay The number of utilization changes (arrival rate changes) per day
	 * @param minServices The minimum number of services running in the data centre
	 * @param maxServices The maximum number of services running in the data centre
	 * @param fullSize True to create applications at their maximum size
	 */
	public void configureRandomApplications(Simulation simulation, double changesPerDay, int minServices, int maxServices, long rampUpTime, long startTime, long duration, boolean fullSize) {

		/*
		 * Configure minimum service level. Create the minimum number of services over the first 40 hours,
		 * and leave them running for the entire simulation.
		 */
		ArrayList<Tuple<Long, Double>> serviceRates = new ArrayList<Tuple<Long, Double>>();
		serviceRates.add(new Tuple<Long, Double>(SimTime.seconds(1), (minServices / SimTime.toHours(rampUpTime))));		
		serviceRates.add(new Tuple<Long, Double>(rampUpTime, 0d));		
		serviceRates.add(new Tuple<Long, Double>(duration, 0d));		// 10 days
		
		ApplicationGenerator appGenerator = new AppManApplicationGenerator(simulation, dcAM, null, serviceRates, fullSize);
		appGenerator.start();
		
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
		
		time = startTime; //start at beginning of 3rd day (end of 2nd)
		
		//loop while we still have simulation time to generate arrival rates for
		while (time < duration) {

			//calculate the next time the rate will changes
			nextTime = time + Math.round(SimTime.days(1) / changesPerDay);
			
			//generate a target VM count to reach by the next rate change
			double target = serviceCountDist.sample();
			
			//caculate the current arrival rate necessary to reach the target VM count
			rate = target / ((nextTime - time) / 1000d / 60d / 60d);
			
			//add the current rate to the list of arrival rates
			serviceRates.add(new Tuple<Long, Double>(time, rate));
			
			//advance to the next time interval
			time = nextTime;
		}
		//add a final rate of 0 to run until the end of the simulation
		serviceRates.add(new Tuple<Long, Double>(duration, 0d));
		
		appGenerator = new AppManApplicationGenerator(simulation, dcAM, new NormalDistribution(SimTime.days(1) / changesPerDay, SimTime.hours(1)), serviceRates, fullSize);
		//appGenerator = new AppManApplicationGenerator(simulation, dcAM, new NormalDistribution(SimTime.days(5), SimTime.hours(1)), serviceRates);
		appGenerator.start();
	}
	
	public class AppManApplicationGenerator extends ApplicationGenerator {
		
		int id;
		boolean fullSize;
		
		public AppManApplicationGenerator(Simulation simulation, AutonomicManager dcTarget, RealDistribution lifespanDist, List<Tuple<Long, Double>> servicesPerHour, boolean fullSize) {
			super(simulation, dcTarget, lifespanDist, servicesPerHour);
			
			this.setArrivalSyncInterval(ARRIVAL_SYNC_INTERVAL);
			this.fullSize = fullSize;
		}

		@Override
		public Application buildApplication() {
			return createApplication(fullSize);
		}
		
		
	}
	
}
