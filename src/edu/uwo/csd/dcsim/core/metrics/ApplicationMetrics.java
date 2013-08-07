package edu.uwo.csd.dcsim.core.metrics;

import java.io.PrintStream;
import java.util.*;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.summary.Sum;

import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.core.Simulation;

public class ApplicationMetrics extends MetricCollection {

	Map<Application, WeightedMetric> cpuUnderProvision = new HashMap<Application, WeightedMetric>();
	Map<Application, WeightedMetric> cpuDemand = new HashMap<Application, WeightedMetric>();
	
	Map<Application, WeightedMetric> slaPenalty = new HashMap<Application, WeightedMetric>();
	Map<Application, WeightedMetric> responseTime = new HashMap<Application, WeightedMetric>();
	Map<Application, WeightedMetric> throughput = new HashMap<Application, WeightedMetric>();
	
	WeightedMetric aggregateCpuUnderProvision = new WeightedMetric();
	WeightedMetric aggregateCpuDemand = new WeightedMetric();
	WeightedMetric aggregateSlaPenalty = new WeightedMetric();
	WeightedMetric aggregateResponseTime = new WeightedMetric();
	WeightedMetric aggregateThroughput = new WeightedMetric();
	
	DescriptiveStatistics slaPenaltyStats;
	DescriptiveStatistics responseTimeStats;
	DescriptiveStatistics throughputStats;
	
	long applicationsSpawned = 0;
	long applicationsShutdown = 0;
	long applicationPlacementsFailed = 0;
	
	public ApplicationMetrics(Simulation simulation) {
		super(simulation);
	}
	
	public void recordApplicationMetrics(Collection<Application> applications) {
		
		double currentCpuUnderProvision = 0;
		double currentCpuDemand = 0;
		double currentSlaPenalty = 0;
		double currentResponseTime = 0;
		double currentThroughput = 0;
		double interactiveApplications = 0;
		
		double val;
		for (Application application : applications) {
			
			if (!cpuUnderProvision.containsKey(application)) {
				cpuUnderProvision.put(application, new WeightedMetric());
				cpuDemand.put(application, new WeightedMetric());
				slaPenalty.put(application, new WeightedMetric());
			}
			
			if (application.getTotalCpuDemand() > application.getTotalCpuScheduled()) {
				val = (double)application.getTotalCpuDemand() - application.getTotalCpuScheduled();
				cpuUnderProvision.get(application).add(val, simulation.getElapsedTime());
				currentCpuUnderProvision += val;
			}
			val = (double)application.getTotalCpuDemand();
			cpuDemand.get(application).add(val, simulation.getElapsedTime());
			currentCpuDemand += val;
			
			if (application.getSla() != null) {
				val = application.getSla().calculatePenalty();
				slaPenalty.get(application).add(val, simulation.getElapsedSeconds());
				currentSlaPenalty += val;
			}
			
			if (application instanceof InteractiveApplication) {
				
				if (!responseTime.containsKey(application)) {
					responseTime.put(application, new WeightedMetric());
					throughput.put(application, new WeightedMetric());
				}
				
				InteractiveApplication interactiveApplication = (InteractiveApplication)application;
				
				val = (double)interactiveApplication.getResponseTime();
				responseTime.get(interactiveApplication).add(val, simulation.getElapsedTime());
				currentResponseTime += val;
				
				val = (double)interactiveApplication.getThroughput();
				throughput.get(interactiveApplication).add(val, simulation.getElapsedTime());
				currentThroughput += val;
				
				++interactiveApplications;
			}
		}

		aggregateCpuUnderProvision.add(currentCpuUnderProvision, simulation.getElapsedTime());
		aggregateCpuDemand.add(currentCpuDemand, simulation.getElapsedTime());
		aggregateSlaPenalty.add(currentSlaPenalty, simulation.getElapsedSeconds());
		aggregateResponseTime.add(currentResponseTime / interactiveApplications, simulation.getElapsedTime());
		aggregateThroughput.add(currentThroughput / interactiveApplications, simulation.getElapsedTime());
	}
	
	@Override
	public void completeSimulation() {
		slaPenaltyStats = new DescriptiveStatistics();
		responseTimeStats = new DescriptiveStatistics();
		throughputStats = new DescriptiveStatistics();
		
		for (Application application : slaPenalty.keySet()) {
			Sum sum = new Sum();
			slaPenaltyStats.addValue(sum.evaluate(slaPenalty.get(application).valuesArray()));
		}
		
		for (Application application : responseTime.keySet()) {
			Mean mean = new Mean();
			responseTimeStats.addValue(mean.evaluate(responseTime.get(application).valuesArray(), 
					responseTime.get(application).weightsArray()));
		}
		
		for (Application application : throughput.keySet()) {
			Mean mean = new Mean();
			throughputStats.addValue(mean.evaluate(throughput.get(application).valuesArray(), 
					throughput.get(application).weightsArray()));
		}
		
	}
	
	public Map<Application, WeightedMetric> getCpuUnderProvision() {
		return cpuUnderProvision;
	}
	
	public Map<Application, WeightedMetric> getCpuDemand() {
		return cpuDemand;
	}
	
	public Map<Application, WeightedMetric> getSlaPenalty() {
		return slaPenalty;
	}
	
	public Map<Application, WeightedMetric> getResponseTime() {
		return responseTime;
	}
	
	public Map<Application, WeightedMetric> getThroughput() {
		return throughput;
	}
	
	public WeightedMetric getAggregateCpuUnderProvision() {
		return aggregateCpuUnderProvision;
	}
	
	public WeightedMetric getAggregateCpuDemand() {
		return aggregateCpuDemand;
	}
	
	public WeightedMetric getAggregateSlaPenalty() {
		return aggregateSlaPenalty;
	}
	
	public WeightedMetric getAggregateResponseTime() {
		return aggregateResponseTime;
	}
	
	public WeightedMetric getAggregateThroughput() {
		return aggregateThroughput;
	}
	
	public DescriptiveStatistics getSlaPenaltyStats() {
		return slaPenaltyStats;
	}
	
	public DescriptiveStatistics getResponseTimeStats() {
		return responseTimeStats;
	}
	
	public DescriptiveStatistics getThroughputStats() {
		return throughputStats;
	}
	
	public long getApplicationsSpawned() {
		return applicationsSpawned;
	}

	public void setApplicationsSpawned(long applicationsSpawned) {
		this.applicationsSpawned = applicationsSpawned;
	}
	
	public void incrementApplicationsSpawned() {
		++applicationsSpawned;
	}
	
	public long getApplicationsShutdown() {
		return applicationsShutdown;
	}
	
	public void setApplicationShutdown(long applicationShutdown) {
		this.applicationsShutdown = applicationShutdown;
	}
	
	public void incrementApplicationShutdown() {
		++applicationsShutdown;
	}
	
	public long getApplicationPlacementsFailed() {
		return applicationPlacementsFailed;
	}
	
	public void setApplicationPlacementFailed(long applicationPlacementFailed) {
		this.applicationPlacementsFailed = applicationPlacementFailed;
	}
	
	public void incrementApplicationPlacementsFailed() {
		++ applicationPlacementsFailed;
	}
	
	public boolean isMVAApproximate() {
		return InteractiveApplication.approximateMVA;
	}

	@Override
	public void printDefault(PrintStream out) {
		out.println("-- APPLICATIONS --");
		out.println("CPU Underprovision");
		out.println("   percentage: " + Utility.toPercentage(getAggregateCpuUnderProvision().getSum() / getAggregateCpuDemand().getSum()) + "%");
		out.println("SLA");
		out.println("  aggregate penalty");
		out.println("    total: " + getAggregateSlaPenalty().getSum());
		out.println("    max: " + getAggregateSlaPenalty().getMax());
		out.println("    mean: " + getAggregateSlaPenalty().getMean());
		out.println("    min: " + getAggregateSlaPenalty().getMin());
		out.println("  per application penalty");
		out.println("    mean: " + getSlaPenaltyStats().getMean());
		out.println("    stdev: " + getSlaPenaltyStats().getStandardDeviation());
		out.println("    max: " + getSlaPenaltyStats().getMax());
		out.println("    95th: " + getSlaPenaltyStats().getPercentile(95));
		out.println("    75th: " + getSlaPenaltyStats().getPercentile(75));
		out.println("    50th: " + getSlaPenaltyStats().getPercentile(50));
		out.println("    25th: " + getSlaPenaltyStats().getPercentile(25));
		out.println("    min: " + getSlaPenaltyStats().getMin());
		out.println("Response Time");
		out.println("    max: " + getAggregateResponseTime().getMax());
		out.println("    mean: " + getAggregateResponseTime().getMean());
		out.println("    min: " + getAggregateResponseTime().getMin());
		out.println("Throughput");
		out.println("    max: " + getAggregateThroughput().getMax());
		out.println("    mean: " + getAggregateThroughput().getMean());
		out.println("    min: " + getAggregateThroughput().getMin());
		out.println("Spawning");
		out.println("   spawned: " + getApplicationsSpawned());
		out.println("   shutdown: " + getApplicationsShutdown());
		out.println("   failed placement: " + getApplicationPlacementsFailed());
		out.print("Interactive Application Model Algorithm: ");
		if (!isMVAApproximate()) {
			out.println("MVA");
		} else {
			out.println("Schweitzer's MVA Approximation");
		}
	}
		
}
