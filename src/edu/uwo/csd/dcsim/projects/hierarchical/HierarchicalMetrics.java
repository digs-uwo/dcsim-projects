package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.application.VmmApplication;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.common.Tuple;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.metrics.MetricCollection;

public class HierarchicalMetrics extends MetricCollection {

	public Map<Application, Double> appSpreadPenalty = new HashMap<Application, Double>();
	DescriptiveStatistics appSpreadPenaltyStats;
	int nZeroSpreadPenalty;
	
	public Map<Application, Long> appVmTime = new HashMap<Application, Long>();
	DescriptiveStatistics appVmTimeStats;
	
	public HierarchicalMetrics(Simulation simulation) {
		super(simulation);
	}
	
	@Override
	public void completeSimulation() {
		appSpreadPenaltyStats = new DescriptiveStatistics();
		
		nZeroSpreadPenalty = 0;
		for (Double penalty : appSpreadPenalty.values()) {
			appSpreadPenaltyStats.addValue(penalty);
			if (penalty == 0)
				nZeroSpreadPenalty++;
		}
		
		appVmTimeStats = new DescriptiveStatistics();
		for (Long time : appVmTime.values())
			appVmTimeStats.addValue(time);
	}
	
	@Override
	public void recordApplicationMetrics(Collection<Application> applications) {
		
		for (Application app : applications) {
			if (app instanceof VmmApplication) continue;
			if (!app.isActive() || app.isComplete()) continue;
			
			// Calculate application placement spread penalty.
			double penalty = 0;
			if (app.getPlacementSpread() > 1) {
				penalty = simulation.getElapsedSeconds();
			}
			addAppSpreadPenalty(app, penalty);
			
			// Calculate application VM time.
			long val = 0;
			if (appVmTime.containsKey(app)) {
				val = appVmTime.get(app);
			}			
			val += simulation.getElapsedTime() * app.getSize();
			appVmTime.put(app, val);
		}
	}

	protected void addAppSpreadPenalty(Application app, double penalty) {
		double val = 0;
		if (appSpreadPenalty.containsKey(app))
			val = appSpreadPenalty.get(app);
		appSpreadPenalty.put(app, val + penalty);
	}
	
	@Override
	public void printDefault(Logger out) {
		out.info("-- APPLICATION MANAGEMENT --");
		out.info("Spread Penalties: ");
		out.info("   total: " + (long)appSpreadPenaltyStats.getSum());
		out.info("   mean: " + Utility.roundDouble(appSpreadPenaltyStats.getMean(), Simulation.getMetricPrecision()));
		out.info("   stdev: " + Utility.roundDouble(appSpreadPenaltyStats.getStandardDeviation(), Simulation.getMetricPrecision()));
		out.info("   max: " + Utility.roundDouble(appSpreadPenaltyStats.getMax(), Simulation.getMetricPrecision()));
		out.info("   min: " + Utility.roundDouble(appSpreadPenaltyStats.getMin(), Simulation.getMetricPrecision()));
		out.info("   zero-penalty: " + nZeroSpreadPenalty + "/" + appSpreadPenalty.size() + " = " + 
				Utility.roundDouble(Utility.toPercentage(nZeroSpreadPenalty / (double)appSpreadPenalty.size()), Simulation.getMetricPrecision()) + "%");
		
		out.info("Application VM time: ");
		out.info("   total: " + SimTime.toHumanReadable((long)appVmTimeStats.getSum()));
		out.info("   mean: " + SimTime.toHumanReadable((long)appVmTimeStats.getMean()));
		out.info("   stdev: " + SimTime.toHumanReadable((long)appVmTimeStats.getStandardDeviation()));
		out.info("   max: " + SimTime.toHumanReadable((long)appVmTimeStats.getMax()));
		out.info("   min: " + SimTime.toHumanReadable((long)appVmTimeStats.getMin()));
	}
	
	@Override
	public List<Tuple<String, Object>> getMetricValues() {
		List<Tuple<String, Object>> metrics = new ArrayList<Tuple<String, Object>>();
		
		metrics.add(new Tuple<String, Object>("spreadPenalty", (long)appSpreadPenaltyStats.getSum()));
		metrics.add(new Tuple<String, Object>("zeroPenalty", Utility.roundDouble(Utility.toPercentage(nZeroSpreadPenalty / (double)appSpreadPenalty.size()), Simulation.getMetricPrecision())));
		metrics.add(new Tuple<String, Object>("appVmTime(Days)", SimTime.toDays((long)appVmTimeStats.getSum())));
		metrics.add(new Tuple<String, Object>("appVmTimeAvg(Hours)", SimTime.toHours((long)appVmTimeStats.getMean())));
		
		return metrics;
	}

}
