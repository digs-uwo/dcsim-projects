package edu.uwo.csd.dcsim.core.metrics;

import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.core.Simulation;

public class CpuUnderprovisionDurationMetric extends Metric {

	private double value = 0;
	
	public CpuUnderprovisionDurationMetric(Simulation simulation, String name) {
		super(simulation, name);
	}

	public void addSlaViolationTime(double time) {
		this.value += time;
	}

	@Override
	public double getValue() {
		return value;
	}

	@Override
	public double getCurrentValue() {
		return value;
	}

	@Override
	public void onStartTimeInterval() {
		//nothing to do
	}

	@Override
	public void onCompleteTimeInterval() {
		//nothing to do
	}
	
	public static CpuUnderprovisionDurationMetric getMetric(Simulation simulation, String name) {
		CpuUnderprovisionDurationMetric metric;
		if (simulation.hasMetric(name)) {
			metric = (CpuUnderprovisionDurationMetric)simulation.getMetric(name);
		}
		else {
			metric = new CpuUnderprovisionDurationMetric(simulation, name);
			simulation.addMetric(metric);
		}
		return metric;	
	}

	@Override
	public String format(double value) {
		return SimTime.toHumanReadable((long)getValue());
	}


}
