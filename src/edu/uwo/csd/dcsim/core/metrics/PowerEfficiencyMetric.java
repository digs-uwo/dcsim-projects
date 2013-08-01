package edu.uwo.csd.dcsim.core.metrics;

import edu.uwo.csd.dcsim.core.Simulation;

public class PowerEfficiencyMetric extends AbstractMetric {

	private double totalCpuUsed = 0;
	private double totalPowerConsumed = 0;
	
	private double currentCpuInUse = 0;
	private double currentPowerConsumption = 0;
	
	public PowerEfficiencyMetric(Simulation simulation, String name) {
		super(simulation, name);
	}

	public void addHostInfo(double cpuInUse, double powerConsumption) {
		totalCpuUsed += cpuInUse * simulation.getElapsedSeconds();
		totalPowerConsumed += powerConsumption * simulation.getElapsedSeconds();
		
		currentCpuInUse += cpuInUse;
		currentPowerConsumption += powerConsumption;
	}
	
	@Override
	public double getValue() {
		return totalCpuUsed / totalPowerConsumed;
	}

	@Override
	public double getCurrentValue() {
		return currentCpuInUse / currentPowerConsumption;
	}

	@Override
	public void onStartTimeInterval() {
		currentCpuInUse = 0;
		currentPowerConsumption = 0;
	}

	@Override
	public void onCompleteTimeInterval() {
		//nothing to do
	}
	
	public static PowerEfficiencyMetric getMetric(Simulation simulation, String name) {
		PowerEfficiencyMetric metric;
		if (simulation.hasMetric(name)) {
			metric = (PowerEfficiencyMetric)simulation.getMetric(name);
		}
		else {
			metric = new PowerEfficiencyMetric(simulation, name);
			simulation.addMetric(metric);
		}
		return metric;	
	}

	@Override
	public String format(double value) {
		return Double.toString(Simulation.roundToMetricPrecision(getValue())) + " cpu/watt";
	}

}
