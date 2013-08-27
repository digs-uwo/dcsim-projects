package edu.uwo.csd.dcsim.projects.applicationManagement.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.application.Task;
import edu.uwo.csd.dcsim.application.TaskInstance;
import edu.uwo.csd.dcsim.application.sla.InteractiveServiceLevelAgreement;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.VmStatusComparator;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.ApplicationManagementMetrics;
import edu.uwo.csd.dcsim.projects.applicationManagement.actions.AddTaskInstanceAction;
import edu.uwo.csd.dcsim.projects.applicationManagement.actions.RemoveTaskInstanceAction;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.ApplicationManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.ApplicationPoolManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.ApplicationPoolManager.ApplicationData;
import edu.uwo.csd.dcsim.projects.applicationManagement.events.TaskInstanceStatusEvent;

public class ApplicationManagementPolicy extends Policy {

	private static final double SLA_WARNING_THRESHOLD = 0.8;
	private static final double SLA_SAFE_THRESHOLD = 0.6;
	private static final double scaleDownFreeze = SimTime.minutes(30);
	
	private static final double CPU_WARNING_THRESHOLD = 0.9;
	private static final double CPU_SAFE_THRESHOLD = 0.5;
	
	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	
	public ApplicationManagementPolicy(double lowerThreshold, double upperThreshold, double targetUtilization) {
		addRequiredCapability(HostPoolManager.class);
		addRequiredCapability(ApplicationPoolManager.class);
		
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}
	
	public void execute() {
		ApplicationPoolManager appPool = manager.getCapability(ApplicationPoolManager.class);
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		Collection<HostData> hosts = hostPool.getHosts();
		
		//update application data
		for (ApplicationData appData : appPool.getApplicationData().values()) {
			Application app = appData.getApplication();
			
			//record application response time
			if (app instanceof InteractiveApplication) {
				InteractiveApplication interactiveApp = (InteractiveApplication)app;
				appData.getApplicationResponseTimes().addValue(interactiveApp.getResponseTime());
				appData.getApplicationResponseTimesLong().addValue(interactiveApp.getResponseTime());
			}
		}
		
		// Reset the sandbox host status to the current host status.
		for (HostData host : hosts) {
			host.resetSandboxStatusToCurrent();
		}
		
		// classify hosts.
		ArrayList<HostData> stressed = new ArrayList<HostData>();
		ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
		ArrayList<HostData> underUtilized = new ArrayList<HostData>();
		ArrayList<HostData> empty = new ArrayList<HostData>();
		
		this.classifyHosts(stressed, partiallyUtilized, underUtilized, empty, hosts);

		//build list of tasks requiring scaling operations
		ArrayList<Task> scaleUpTasks = new ArrayList<Task>();
		ArrayList<Task> scaleDownTasks = new ArrayList<Task>();
		evaluateScaling(scaleUpTasks, scaleDownTasks, appPool);
		
		
		//TODO implement a window, or time-out for a stressed host to remain stressed while we wait for non-migration options
		
		//handle stressed hosts
		for (HostData host : stressed) {
			//check if any scale down operations involve this host, if so, perform only enough to relieve stress
			Resources resourcesInUse = host.getCurrentStatus().getResourcesInUse();
			int cpuOverThreshold = resourcesInUse.getCpu() - (int)(host.getHostDescription().getResourceCapacity().getCpu() * upperThreshold);
			
			TaskInstance targetInstance = null;
			int targetCpu = 0;
			
			//TODO search through scaleUpTasks to find tasks with instances on this host, check if scale up will relieve stress situation (could match relocation interval)
			
			for (Task task : scaleDownTasks) {
				
				for (TaskInstance instance : task.getInstances()) {
					if (instance.getVM().getVMAllocation().getHost() == host.getHost()) {
						if (instance.getResourceScheduled().getCpu() > targetCpu &&
								instance.getResourceScheduled().getCpu() >= cpuOverThreshold) {
							targetInstance = instance;
							targetCpu = instance.getResourceScheduled().getCpu();
						}
					}
				}
			}
			
			if (targetInstance != null) {
				scaleDownTasks.remove(targetInstance.getTask());
				RemoveTaskInstanceAction action = new RemoveTaskInstanceAction(targetInstance);
				action.execute(simulation, this);
				
//				System.out.println(SimTime.toHumanReadable(simulation.getSimulationTime()) + " Removing Instance from Task " + 
//						targetInstance.getTask().getApplication().getId() + "-" + targetInstance.getTask().getId() + 
//						" on Host #" + host.getId());
				
			} else {
				//otherwise, choose a vm to migrate. Save the selection to avoid migrating to hosts that will be shutdown
				
				//just keep list of stressed hosts to migrate from?
			}
		}
		

		//sort underutilized by ease of shutdown/shutdown cost (number of task instances with scaling down tasks)
		
		//attempt to place VMs from remaining stressed hosts, first partially utilized, then underutilized with highest shutdown cost
		
		//attempt to place VMs from scaling up applications, then underutilized with highest shutdown cost	
		
		//implement a window or timeout for underutilized to remain underutilized while we wait for non-migration options (could match consolidation interval)
		//for underutilized hosts not chosen as targets
			//check all remaining tasks requiring scale down
			//select task scale down such that maximize number of VMs removed from fewer hosts
			//migrate if underutilized timeout exceeded
				
		//complete scale down actions for applications that have not had their scaling executed in previous steps - what priority for instance selection?
		
	}
	
	public void execute(TaskInstanceStatusEvent event) {
		ApplicationPoolManager appPool = manager.getCapability(ApplicationPoolManager.class);
		TaskInstance instance = event.getTaskInstance();
		Application application = instance.getTask().getApplication();
		
		ApplicationData appData = appPool.getApplicationData(application);
		if (!appPool.getApplicationData(application).getInstanceManagers().containsKey(instance)) throw new RuntimeException("Task Instance not found in ApplicationManager instance map. Should not happen.");
		
		appData.getInstanceCpuUtils().get(instance).addValue(event.getCpuUtil());
		appData.getInstanceResponseTimes().get(instance).addValue(event.getResponseTime());
		
		appData.getInstanceCpuUtilsLong().get(instance).addValue(event.getCpuUtil());
		appData.getInstanceResponseTimesLong().get(instance).addValue(event.getResponseTime());
	}
	
	protected void evaluateScaling(ArrayList<Task> scaleUpTasks, ArrayList<Task> scaleDownTasks, ApplicationPoolManager appPool) {
		
		for (ApplicationData appData : appPool.getApplicationData().values()) {
			Application app = appData.getApplication();
			
			boolean slaWarning = false;
			boolean slaSafe = false;
			
			if (app instanceof InteractiveApplication && app.getSla() instanceof InteractiveServiceLevelAgreement) {
				InteractiveServiceLevelAgreement sla = (InteractiveServiceLevelAgreement)app.getSla();
				
				//predict response time for next interval
				SimpleRegression regression = new SimpleRegression();
				int i = 0;
				for (double val : appData.getApplicationResponseTimes().getValues()) {
					regression.addData(i++, val);
				}
				
				double rt = appData.getApplicationResponseTimes().getMean();
				
				if (rt >= (sla.getResponseTime() * SLA_WARNING_THRESHOLD) && regression.getSlope() > 0) {
					slaWarning = true;
				} else if (	rt < (sla.getResponseTime() * SLA_SAFE_THRESHOLD ) && regression.getSlope() < 0) {
					slaSafe = true;
				}
			}
			
			if (slaWarning) {
	
				//Select Task to scale up
				Task targetTask = null;
				double targetIncrease = 0;
				
				//choose task which has had the fastest increase in response time
				for (Task task : app.getTasks()) {
					double avgSlope = 0;
					double avgIncrease = 0;
					for (TaskInstance instance : task.getInstances()) {
						DescriptiveStatistics instanceRTs = appData.getInstanceResponseTimes().get(instance);
						
						double[] vals = instanceRTs.getValues();
						avgIncrease += vals[vals.length - 1] - vals[0];
						
					}
					
					avgIncrease = avgIncrease / task.getInstances().size();
					if (avgIncrease > targetIncrease) {
						targetIncrease = avgIncrease;
						targetTask = task;
					}
				}
						
				if (targetTask != null) {
					scaleUpTasks.add(targetTask);
					simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).instancesAdded++;
					appData.setLastScaleUp(simulation.getSimulationTime());
					
	//				System.out.println(SimTime.toHumanReadable(simulation.getSimulationTime()) + " Adding Instance to Task " + app.getId() + "-" + targetTask.getId());
				}
				
			} else if (slaSafe && (simulation.getSimulationTime() - appData.getLastScaleUp()) > scaleDownFreeze) {
				
				//look for an underutilized task to scale down (choose lowest utilization task)
				Task targetTask = null;
				double targetUtil = Double.MAX_VALUE;
				
				for (Task task : app.getTasks()) {
					if (task.getInstances().size() > 1) {
						double utilization = 0;
						for (TaskInstance instance : task.getInstances()) {
							utilization += appData.getInstanceCpuUtilsLong().get(instance).getMean();
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
					scaleDownTasks.add(targetTask);

					simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).instancesRemoved++;
							
	//				System.out.println(SimTime.toHumanReadable(simulation.getSimulationTime()) + " Removing Instance from Task " + app.getId() + "-" + targetTask.getId());
					
				}
			}
		
		}
	}
	
	protected void classifyHosts(ArrayList<HostData> stressed, 
			ArrayList<HostData> partiallyUtilized, 
			ArrayList<HostData> underUtilized, 
			ArrayList<HostData> empty,
			Collection<HostData> hosts) {
		
		for (HostData host : hosts) {
			
			// Filter out hosts with a currently invalid status.
			if (host.isStatusValid()) {
					
				// Calculate host's avg CPU utilization over the last window of time.
				double avgCpuInUse = 0;
				int count = 0;
				for (HostStatus status : host.getHistory()) {
					// Only consider times when the host is powered on.
					if (status.getState() == Host.HostState.ON) {
						avgCpuInUse += status.getResourcesInUse().getCpu();
						++count;
					}
					else
						break;
				}
				if (count != 0) {
					avgCpuInUse = avgCpuInUse / count;
				}
				
				double avgCpuUtilization = Utility.roundDouble(avgCpuInUse / host.getHostDescription().getResourceCapacity().getCpu());
				
				// Classify hosts.
				if (host.getCurrentStatus().getVms().size() == 0) {
					empty.add(host);
				} else if (avgCpuUtilization < lowerThreshold) {
					underUtilized.add(host);
				} else if (avgCpuUtilization > upperThreshold) {
					stressed.add(host);
				} else {
					partiallyUtilized.add(host);
				}
			}
		}
	}
	
	private ArrayList<VmStatus> orderSourceVms(ArrayList<VmStatus> sourceVms, HostData source) {
		ArrayList<VmStatus> sorted = new ArrayList<VmStatus>();
		
		// Remove VMs with less CPU load than the CPU load by which the source 
		// host is stressed.
		double cpuExcess = source.getSandboxStatus().getResourcesInUse().getCpu() - source.getHostDescription().getResourceCapacity().getCpu() * this.upperThreshold;
		for (VmStatus vm : sourceVms)
			if (vm.getResourcesInUse().getCpu() >= cpuExcess)
				sorted.add(vm);
		
		if (!sorted.isEmpty())
			// Sort VMs in increasing order by CPU load.
			Collections.sort(sorted, VmStatusComparator.getComparator(VmStatusComparator.CPU_IN_USE));
		else {
			// Add original list of VMs and sort them in decreasing order by 
			// CPU load, so as to avoid trying to migrate the smallest VMs 
			// first (which would not help resolve the stress situation).
			sorted.addAll(sourceVms);
			Collections.sort(sorted, VmStatusComparator.getComparator(VmStatusComparator.CPU_IN_USE));
			Collections.reverse(sorted);
		}
		
		return sorted;
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
