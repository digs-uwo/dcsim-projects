package edu.uwo.csd.dcsim.strategyTree;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.host.power.*;
import edu.uwo.csd.dcsim.core.metrics.Metric;
import edu.uwo.csd.dcsim.vm.*;
import edu.uwo.csd.dcsim.application.Application;
import java.util.ArrayList;
import java.text.*;

public class DirNode extends Node{

	DataCentre dc;
	String metricName;
	String operator;
	double threshold;
	double measurementTotal = 0;
	double numMeasurements = 0;
	
	private double min, max;
	
	double runningTotal = 0;
	public DirNode(String name, DataCentre dc, int evalInterval, int lvl, String metricName, String operator, double threshold){
		super(name, dc, new Node[0], evalInterval, lvl);
		this.dc = dc;
		this.metricName = metricName;
		this.operator = operator;
		this.threshold = threshold;
		
		min = 1000000;
		max = -1;
	}
	
	public void evaluate(){
		//System.out.println("Evaluating: " + name);
		
		if(metricName.equals("slaViolation")){
			results[iteration] = evalSLA();
		}else if(metricName.equals("power")){
			results[iteration] = evalPower();
		}else if(metricName.equals("powerEfficiency")){
			results[iteration] = evalPowerEfficiency();
		}else{
			System.err.println("Error in Strategy Tree: No evaluator available for metric: " + metricName);
		}
		/*
		if((results[iteration] < min) || (results[iteration] > max)){
			if(results[iteration] < min){
				min = results[iteration];
			}
			if(results[iteration] > max){
				max = results[iteration];
			}
			System.out.println("DirNode: " + name + " shows min: " + min + ", max: " + max);
		}*/
		iteration++;
	}
	private double evalSLA(){
		
		ArrayList<Host> hosts = dc.getHosts();
		
		double totalWork = 0;
		double totalSlaWork = 0;
		
		for(Host host : hosts){
			ArrayList<VMAllocation> vmAllocations = host.getVMAllocations();
			for(VMAllocation vmAllocation : vmAllocations){
				VM vm = vmAllocation.getVm();
				Application application = vm.getApplication();
				totalSlaWork += application.getSLAViolatedWork();
				
				//this is necessary in case slaWork is zero and slaPercent is 1.0.  Then need to know the total work
				totalWork += application.getWork();
			}
		}
		
		if(totalWork > 0){
			numMeasurements++;
			measurementTotal += (totalSlaWork/totalWork);
		}
		
		//if no work, something's wrong
		if(totalWork == 0){
			System.out.println("ERROR!!!");
		}
		
		//System.out.println(totalSlaWork / totalWork);
		
		return totalSlaWork/totalWork;
		
	}
	
	private double evalPower(){
		ArrayList<Host> hosts = dc.getHosts();
		double powerTotal = 0;
		for(Host host : hosts){
			powerTotal += host.getCurrentPowerConsumption();
		}
		
		return powerTotal;
	}
	
	private double evalPowerEfficiency(){
		double optimalEff = getOptimalEfficiency();
		double currentEff = getCurrentEfficiency();
		
		DecimalFormat df = new DecimalFormat("#.######");
		//System.out.println("Current Power Efficiency Ratio: " + df.format(currentEff/optimalEff));
				
		return currentEff/optimalEff;
	}
	
	private double getOptimalEfficiency(){
		ArrayList<Host> hosts = dc.getHosts();
		
		//start with arbitrarily large number
		double bestYet = 1000000;
		
		for(Host host : hosts){
			
			//get total possible power consumption
			HostPowerModel model = host.getPowerModel();
			double power = model.getPowerConsumption(1);
			
			//get total possible capacity
			double capacity = host.getTotalCpu();
			
			//if this machine is more efficient than any seen yet
			if(capacity/power < bestYet){
				bestYet = power/capacity;
			}
		}
		return bestYet;
	}
	
	private double getCurrentEfficiency(){
		//get total current power consumption and workload
		ArrayList<Host> hosts = dc.getHosts();
		double totalPower = 0;
		double workload = 0;
		for(Host host : hosts){
			
			//power consumption
			totalPower += host.getCurrentPowerConsumption();
			
			//workload
			workload += host.getCpuManager().getCpuInUse();
			
			/*
			ArrayList<VMAllocation> vmAllocations = host.getVMAllocations();
			for(VMAllocation vmAllocation : vmAllocations){
				VM vm = vmAllocation.getVm();
				Application application = vm.getApplication();
				workload += application.getWork();
			}*/
		}
		
		//return overall efficiency
		return totalPower/workload;
	}
	
	public double getResult(){
		iteration = 0;
		double res = 0;
		for(int i=0; i<interval; i++){
			res += results[i];
		}
		
		res /= interval;
		if (operator.equals("<")){
			return (res < threshold) ? 1.0 : -1.0;
		}else{
			return (res > threshold) ? 1.0 : -1.0;
		}
	}
	
	public double getAvgMeasurement(){
		return measurementTotal / numMeasurements;
	}
}
