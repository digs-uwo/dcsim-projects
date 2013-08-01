package edu.uwo.csd.dcsim.core.metrics;

import java.util.*;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.summary.Sum;

import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.core.Simulation;

public class ApplicationMetrics extends MetricCollection {

	Map<Application, WeightedMetric> cpuUnderProvision = new HashMap<Application, WeightedMetric>();
	Map<Application, WeightedMetric> cpuDemand = new HashMap<Application, WeightedMetric>();
	
	Map<Application, WeightedMetric> slaPenalty = new HashMap<Application, WeightedMetric>();
	Map<Application, WeightedMetric> responseTime = new HashMap<Application, WeightedMetric>();
	Map<Application, WeightedMetric> throughput = new HashMap<Application, WeightedMetric>();
	
	WeightedMetric aggregateCpuUnderProvision = new WeightedMetric(this);
	WeightedMetric aggregateCpuDemand = new WeightedMetric(this);
	WeightedMetric aggregateSlaPenalty = new WeightedMetric(this);
	WeightedMetric aggregateResponseTime = new WeightedMetric(this);
	WeightedMetric aggregateThroughput = new WeightedMetric(this);
	
	DescriptiveStatistics slaPenaltyStats;
	DescriptiveStatistics responseTimeStats;
	DescriptiveStatistics throughputStats;
	
	public ApplicationMetrics(Simulation simulation) {
		super(simulation);
	}
	
	public void recordApplicationMetrics(Collection<Application> applications) {
		
		double currentCpuUnderProvision = 0;
		double currentCpuDemand = 0;
		double currentSlaPenalty = 0;
		double currentResponseTime = 0;
		double currentThroughput = 0;
		
		updateTimeWeights();
		
		double val;
		for (Application application : applications) {
			
			if (!cpuUnderProvision.containsKey(application)) {
				cpuUnderProvision.put(application, new WeightedMetric(this));
				cpuDemand.put(application, new WeightedMetric(this));
				slaPenalty.put(application, new WeightedMetric(this));
			}
			
			if (application.getTotalCpuDemand() > application.getTotalCpuScheduled()) {
				val = (double)application.getTotalCpuDemand() - application.getTotalCpuScheduled();
				cpuUnderProvision.get(application).add(val);
				currentCpuUnderProvision += val;
			}
			val = (double)application.getTotalCpuDemand();
			cpuDemand.get(application).add(val);
			currentCpuDemand += val;
			
			if (application.getSla() != null) {
				val = application.getSla().calculatePenalty();
				slaPenalty.get(application).add(val);
				currentSlaPenalty += val;
			}
			
			if (application instanceof InteractiveApplication) {
				
				if (!responseTime.containsKey(application)) {
					responseTime.put(application, new WeightedMetric(this));
					throughput.put(application, new WeightedMetric(this));
				}
				
				InteractiveApplication interactiveApplication = (InteractiveApplication)application;
				
				val = (double)interactiveApplication.getResponseTime();
				responseTime.get(interactiveApplication).add(val);
				currentResponseTime += val;
				
				val = (double)interactiveApplication.getThroughput();
				throughput.get(interactiveApplication).add(val);
				currentThroughput += val;
			}
		}
		
		aggregateCpuUnderProvision.add(currentCpuUnderProvision);
		aggregateCpuDemand.add(currentCpuDemand);
		aggregateSlaPenalty.add(currentSlaPenalty);
		aggregateResponseTime.add(currentResponseTime);
		aggregateThroughput.add(currentThroughput);
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
	
	DescriptiveStatistics getSlaPenaltyStats() {
		return slaPenaltyStats;
	}
	
	DescriptiveStatistics getResponseTimeStats() {
		return responseTimeStats;
	}
	
	DescriptiveStatistics getThroughputStats() {
		return throughputStats;
	}
	
	@Override
	public void completeSimulation() {
		slaPenaltyStats = new DescriptiveStatistics();
		responseTimeStats = new DescriptiveStatistics();
		throughputStats = new DescriptiveStatistics();
		
		for (Application application : slaPenalty.keySet()) {
			Sum sum = new Sum();
			slaPenaltyStats.addValue(sum.evaluate(slaPenalty.get(application).toDoubleArray()));
		}
		
		for (Application application : responseTime.keySet()) {
			Mean mean = new Mean();
			responseTimeStats.addValue(mean.evaluate(responseTime.get(application).toDoubleArray(), 
					toDoubleArray(timeWeights)));
		}
		
		for (Application application : throughput.keySet()) {
			Mean mean = new Mean();
			throughputStats.addValue(mean.evaluate(throughput.get(application).toDoubleArray(), 
					toDoubleArray(timeWeights)));
		}
		
	}

	@Override
	public void completeTimeStep() {
		// TODO Auto-generated method stub
		
	}
		
}
