package edu.uwo.csd.dcsim.projects.applicationManagement.policies;

import java.util.Collection;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import edu.uwo.csd.dcsim.application.*;
import edu.uwo.csd.dcsim.application.sla.InteractiveServiceLevelAgreement;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.ApplicationManagementMetrics;
import edu.uwo.csd.dcsim.projects.applicationManagement.actions.AddTaskInstanceAction;
import edu.uwo.csd.dcsim.projects.applicationManagement.actions.RemoveTaskInstanceAction;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.ApplicationManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.events.TaskInstanceStatusEvent;

public class ApplicationScalingPolicy extends Policy {

	private static final double SLA_WARNING_THRESHOLD = 0.8;
	private static final double SLA_SAFE_THRESHOLD = 0.6;
	private static final double scaleDownFreeze = SimTime.minutes(30);
	
	private static final double CPU_WARNING_THRESHOLD = 0.9;
	private static final double CPU_SAFE_THRESHOLD = 0.5;
	
	private AutonomicManager dcManager;
	private boolean slaAware;
	long lastScaleUp = Long.MIN_VALUE;
	
	public ApplicationScalingPolicy(AutonomicManager dcManager, boolean slaAware) {
		addRequiredCapability(ApplicationManager.class);
		this.dcManager = dcManager;
		
		this.slaAware = slaAware;
	}
	
	public void execute(TaskInstanceStatusEvent event) {
		ApplicationManager appManager = manager.getCapability(ApplicationManager.class);
		TaskInstance instance = event.getTaskInstance();
		
		if (!appManager.getInstanceManagers().containsKey(instance)) throw new RuntimeException("Task Instance not found in ApplicationManager instance map. Should not happen.");
		
		appManager.getInstanceCpuUtils().get(instance).addValue(event.getCpuUtil());
		appManager.getInstanceResponseTimes().get(instance).addValue(event.getResponseTime());
		
		appManager.getInstanceCpuUtilsLong().get(instance).addValue(event.getCpuUtil());
		appManager.getInstanceResponseTimesLong().get(instance).addValue(event.getResponseTime());
	}
	
	public void execute() {
		ApplicationManager appManager = manager.getCapability(ApplicationManager.class);
		Application app = appManager.getApplication();
		
		//record application response time
		if (app instanceof InteractiveApplication) {
			InteractiveApplication interactiveApp = (InteractiveApplication)app;
			appManager.getApplicationResponseTimes().addValue(interactiveApp.getResponseTime());
			appManager.getApplicationResponseTimesLong().addValue(interactiveApp.getResponseTime());
		}
		
		if (slaAware) {
			autoscaleOnSLA();
		} else {
			autoscaleOnCpu();
		}
	}
	
	public void autoscaleOnSLA() {
		ApplicationManager appManager = manager.getCapability(ApplicationManager.class);
		Application app = appManager.getApplication();
		
		HostPoolManager hostPool = dcManager.getCapability(HostPoolManager.class);
		
		
		boolean slaWarning = false;
		boolean slaSafe = false;
		if (app instanceof InteractiveApplication && app.getSla() instanceof InteractiveServiceLevelAgreement) {
			InteractiveApplication interactiveApp = (InteractiveApplication)app;
			InteractiveServiceLevelAgreement sla = (InteractiveServiceLevelAgreement)app.getSla();
			
			//predict response time for next interval
			SimpleRegression regression = new SimpleRegression();
			int i = 0;
			for (double val : appManager.getApplicationResponseTimes().getValues()) {
				regression.addData(i++, val);
			}
			
			double rt = appManager.getApplicationResponseTimes().getMean();
//			double rt = interactiveApp.getResponseTime();
//			double rt = regression.predict(appManager.getApplicationResponseTimes().getN());
			
			if (rt >= (sla.getResponseTime() * SLA_WARNING_THRESHOLD) && regression.getSlope() > 0) {
				slaWarning = true;
			} else if (	rt < (sla.getResponseTime() * SLA_SAFE_THRESHOLD ) && regression.getSlope() < 0) {
				slaSafe = true;
			}
		}
		
		if (slaWarning) {

			//Select Task to scale up
			Task targetTask = null;
			double targetSlope = -1 * Double.MAX_VALUE;
			double targetIncrease = 0;
			
			//choose task which has had the fastest increase in response time
			for (Task task : app.getTasks()) {
				double avgSlope = 0;
				double avgIncrease = 0;
				for (TaskInstance instance : task.getInstances()) {
					DescriptiveStatistics instanceRTs = appManager.getInstanceResponseTimes().get(instance);
					SimpleRegression rtRegression = new SimpleRegression();
					int i = 0;
					for (double val : instanceRTs.getValues()) {
						rtRegression.addData(i++, val);
					}
					avgSlope += rtRegression.getSlope();
					
					double[] vals = instanceRTs.getValues();
					avgIncrease += vals[vals.length - 1] - vals[0];
					
				}
				
				avgSlope = avgSlope / task.getInstances().size();
//				if (avgSlope > targetSlope && avgSlope > 0) {
//					targetSlope = avgSlope;
//					targetTask = task;
//				}
				
				avgIncrease = avgIncrease / task.getInstances().size();
				if (avgIncrease > targetIncrease) {
					targetIncrease = avgIncrease;
					targetTask = task;
				}
			}
					
			if (targetTask != null) {
				AddTaskInstanceAction action = new AddTaskInstanceAction(targetTask, dcManager);
				action.execute(simulation, this);
				simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).instancesAdded++;
				lastScaleUp = simulation.getSimulationTime();
				
//				System.out.println(SimTime.toHumanReadable(simulation.getSimulationTime()) + " Adding Instance to Task " + app.getId() + "-" + targetTask.getId());
			}
			
		} else if (slaSafe && (simulation.getSimulationTime() - lastScaleUp) > scaleDownFreeze) {
			
			//look for an underutilized task to scale down (choose lowest utilization task)
			Task targetTask = null;
			double targetUtil = Double.MAX_VALUE;
			
			for (Task task : app.getTasks()) {
				if (task.getInstances().size() > 1) {
					double utilization = 0;
					for (TaskInstance instance : task.getInstances()) {
						utilization += appManager.getInstanceCpuUtilsLong().get(instance).getMean();
					}
					if ((utilization / (task.getInstances().size() - 1)) < CPU_SAFE_THRESHOLD) {
						if (utilization < targetUtil) {
							targetUtil = utilization;
							targetTask = task;
						}
					}
				}
			}
			
			if (targetTask != null) {			
				double targetHostUtil = -1;
				TaskInstance targetInstance = null;
				
				//choose a task instance to shut down (instance on host with highest utilization to reduce overload risk)
				for (TaskInstance instance : targetTask.getInstances()) {
					double util = instance.getVM().getVMAllocation().getHost().getResourceManager().getCpuUtilization();
					if (util > targetHostUtil) {
						targetHostUtil = util;
						targetInstance = instance;
					}
				}
				
				RemoveTaskInstanceAction action = new RemoveTaskInstanceAction(targetInstance);
				action.execute(simulation, this);
				simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).instancesRemoved++;
						
				//invalidate the dcManager host data
				if (hostPool != null) {
					HostData hostData = hostPool.getHost(targetInstance.getVM().getVMAllocation().getHost().getId());
					hostData.invalidateStatus(simulation.getSimulationTime());
				}
				
//				System.out.println(SimTime.toHumanReadable(simulation.getSimulationTime()) + " Removing Instance from Task " + app.getId() + "-" + targetTask.getId());
				
			}
		}
		
	}
	
	public void autoscaleOnCpu() {
		ApplicationManager appManager = manager.getCapability(ApplicationManager.class);
		Application app = appManager.getApplication();
		
		HostPoolManager hostPool = dcManager.getCapability(HostPoolManager.class);
		
		boolean scaledUp = false;
		
		//scale up - look for tasks with high utilization
		Task targetTask = null;
		double targetUtil = 0;
		
		for (Task task : app.getTasks()) {
			double taskUtil = 0;
			for (TaskInstance instance : task.getInstances()) {
				taskUtil += appManager.getInstanceCpuUtils().get(instance).getMean();
			}
			taskUtil = taskUtil / task.getInstances().size();
			
			if (taskUtil > CPU_WARNING_THRESHOLD && taskUtil > targetUtil) {
				targetUtil = taskUtil;
				targetTask = task;
			}
		}
		
		if (targetTask != null) {
			AddTaskInstanceAction action = new AddTaskInstanceAction(targetTask, dcManager);
			action.execute(simulation, this);
			simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).instancesAdded++;
			lastScaleUp = simulation.getSimulationTime();
			scaledUp = true;
			
//			System.out.println(SimTime.toHumanReadable(simulation.getSimulationTime()) + " Adding Instance to Task " + app.getId() + "-" + targetTask.getId());
		}
		
		//scale down - choose task with lowest utilization that will result in utilization remaining below the safe threshold
		if (!scaledUp && (simulation.getSimulationTime() - lastScaleUp) > scaleDownFreeze) {
			targetUtil = Double.MAX_VALUE;
			
			for (Task task : app.getTasks()) {
				if (task.getInstances().size() > 1) {
					double utilization = 0;
					for (TaskInstance instance : task.getInstances()) {
						utilization += appManager.getInstanceCpuUtils().get(instance).getMean();
					}

					if ((utilization / (task.getInstances().size() - 1)) < CPU_SAFE_THRESHOLD) {
						if (utilization < targetUtil) {
							targetUtil = utilization;
							targetTask = task;
						}
					}
				}
			}
			
			if (targetTask != null) {

				double targetHostUtil = -1;
				TaskInstance targetInstance = null;
				
				//choose a task instance to shut down (instance on host with highest utilization to reduce overload risk)
				for (TaskInstance instance : targetTask.getInstances()) {
					double util = instance.getVM().getVMAllocation().getHost().getResourceManager().getCpuUtilization();
					if (util > targetHostUtil) {
						targetHostUtil = util;
						targetInstance = instance;
					}
				}
				
				RemoveTaskInstanceAction action = new RemoveTaskInstanceAction(targetInstance);
				action.execute(simulation, this);
				simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).instancesRemoved++;

				//invalidate the dcManager host data
				if (hostPool != null) {
					HostData hostData = hostPool.getHost(targetInstance.getVM().getVMAllocation().getHost().getId());
					hostData.invalidateStatus(simulation.getSimulationTime());
				}
				
//				System.out.println(SimTime.toHumanReadable(simulation.getSimulationTime()) + " Removing Instance from Task " + app.getId() + "-" + targetTask.getId());
			}
			
		}
	}
	
	@Override
	public void onInstall() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStart() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStop() {
		// TODO Auto-generated method stub
		
	}

}
