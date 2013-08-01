package edu.uwo.csd.dcsim.core.metrics;

import edu.uwo.csd.dcsim.core.Simulation;

public class CpuUnderprovisionMetric extends AbstractMetric {

	private double totalSlaVWork = 0;
	private double totalWork = 0;
	
	private double currentSlaVWork = 0;
	private double currentWork = 0;
	
	public CpuUnderprovisionMetric(Simulation simulation, String name) {
		super(simulation, name);
	}

	public void addSlaVWork(double slaVWork) {
		totalSlaVWork += slaVWork;
		currentSlaVWork += slaVWork;
	}
	
	public void addWork(double work) {
		totalWork += work;
		currentWork += work;
	}

	@Override
	public double getValue() {
		return totalSlaVWork / totalWork;
	}

	@Override
	public double getCurrentValue() {
		return currentSlaVWork / currentWork;
	}

	@Override
	public void onStartTimeInterval() {
		currentSlaVWork = 0;
		currentWork = 0;
	}

	@Override
	public void onCompleteTimeInterval() {
		//nothing to do
	}
	
	public static CpuUnderprovisionMetric getMetric(Simulation simulation, String name) {
		CpuUnderprovisionMetric metric;
		if (simulation.hasMetric(name)) {
			metric = (CpuUnderprovisionMetric)simulation.getMetric(name);
		}
		else {
			metric = new CpuUnderprovisionMetric(simulation, name);
			simulation.addMetric(metric);
		}
		return metric;	
	}

	@Override
	public String format(double value) {
		return Double.toString(Simulation.roundToMetricPrecision(getValue() * 100)) + "%";
	}

}
