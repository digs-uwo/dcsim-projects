package edu.uwo.csd.dcsim.projects.applicationManagement.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.application.Task;
import edu.uwo.csd.dcsim.application.TaskInstance;
import edu.uwo.csd.dcsim.application.sla.InteractiveServiceLevelAgreement;
import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.common.Tuple;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.host.Host.HostState;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.HostDataComparator;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.VmStatusComparator;
import edu.uwo.csd.dcsim.management.action.ConcurrentManagementActionExecutor;
import edu.uwo.csd.dcsim.management.action.InstantiateVmAction;
import edu.uwo.csd.dcsim.management.action.MigrationAction;
import edu.uwo.csd.dcsim.management.action.SequentialManagementActionExecutor;
import edu.uwo.csd.dcsim.management.action.ShutdownHostAction;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.ApplicationManagementMetrics;
import edu.uwo.csd.dcsim.projects.applicationManagement.actions.AddTaskInstanceAction;
import edu.uwo.csd.dcsim.projects.applicationManagement.actions.RemoveTaskInstanceAction;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.ApplicationManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.ApplicationPoolManager;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.ApplicationPoolManager.ApplicationData;
import edu.uwo.csd.dcsim.projects.applicationManagement.events.TaskInstanceStatusEvent;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;
import edu.uwo.csd.dcsim.vm.VmDescription;

public class ApplicationManagementPolicy extends Policy {

	private static final double STRESS_WINDOW = 4; //* 5 min intervals = 10 min
	private static final double UNDERUTIL_WINDOW = 12; //* 5 min intervals = 60 min
	
	private double slaWarningThreshold = 0.8;
	private double slaSafeThreshold = 0.6;
	private double scaleDownFreeze = SimTime.minutes(60);	
	private double cpuSafeThreshold = 0.5;
	private double lowerThreshold;
	private double upperThreshold;
	private double targetUtilization;

	private HashMap<HostData, Integer> stressedHostWindow = new HashMap<HostData, Integer>();
	private HashMap<HostData, Integer> underutilHostWindow = new HashMap<HostData, Integer>();
	
	public ApplicationManagementPolicy(double lowerThreshold, double upperThreshold, double targetUtilization) {
		addRequiredCapability(HostPoolManager.class);
		addRequiredCapability(ApplicationPoolManager.class);
		
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
	}
	
	public void setParameters(double slaWarningThreshold, 
			double slaSafeThreshold,
			long scaleDownFreeze,
			double cpuSafeThreshold) {
		
		this.slaWarningThreshold = slaWarningThreshold;
		this.slaSafeThreshold = slaSafeThreshold;
		this.scaleDownFreeze = scaleDownFreeze;
		this.cpuSafeThreshold = cpuSafeThreshold;
	
	}
	
	public void execute() {
			
		ApplicationPoolManager appPool = manager.getCapability(ApplicationPoolManager.class);
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		Collection<HostData> hosts = new ArrayList<HostData>();		
		
		//filter out invalid host status
		for (HostData host : hostPool.getHosts()) {
			if (host.isStatusValid()) {
				hosts.add(host);
			}
		}
		
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
		
		ConcurrentManagementActionExecutor shutdownActions = new ConcurrentManagementActionExecutor();
		ConcurrentManagementActionExecutor migrations = new ConcurrentManagementActionExecutor();
		
		// classify hosts.
		ArrayList<HostData> stressed = new ArrayList<HostData>();
		ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
		ArrayList<HostData> underUtilized = new ArrayList<HostData>();
		ArrayList<HostData> empty = new ArrayList<HostData>();

		this.classifyHosts(stressed, partiallyUtilized, underUtilized, empty, hosts);
		
		//update history of stressed and underutilized hosts
		updateHostWindows(stressed, underUtilized);

		//build list of tasks requiring scaling operations
		ArrayList<Task> scaleUpTasks = new ArrayList<Task>();
		ArrayList<Task> scaleDownTasks = new ArrayList<Task>();
		evaluateScaling(scaleUpTasks, scaleDownTasks, appPool);
				
		//handle stressed hosts
		ArrayList<HostData> stressedPendingMigration = new ArrayList<HostData>();
		ArrayList<HostData> stressedHostsAddressed = new ArrayList<HostData>();
		
		for (HostData host : stressed) {		
			
			//check if any scale down operations involve this host, if so, perform only enough to relieve stress
			Resources resourcesInUse = host.getCurrentStatus().getResourcesInUse();
			int cpuOverThreshold = resourcesInUse.getCpu() - (int)(host.getHostDescription().getResourceCapacity().getCpu() * upperThreshold);
			
			TaskInstance targetInstance = null;
			int targetCpu = 0;
			
			//search through scaleUpTasks to find tasks with instances on this host, check if scale up will relieve stress situation
			int estimatedCpuRelief = 0;
			for (Task task : scaleUpTasks) {
				int newInstanceCpu = 0;				
				
				//estimate new, reduced instance CPU usage
				for (TaskInstance instance : task.getInstances()) {
					newInstanceCpu += instance.getResourceScheduled().getCpu();
				}
				newInstanceCpu = newInstanceCpu / task.getInstances().size() + 1;
				
				for (TaskInstance instance : task.getInstances()) {
					if (instance.getVM().getVMAllocation().getHost().getId() == host.getId()) {
						estimatedCpuRelief += (instance.getResourceScheduled().getCpu() - newInstanceCpu);
					}
				}
			}
			
			/* if we estimate that the stress situation will be relieved by scale up actions, we don't perform other
			 * actions to relieve it. This allows task instances to be selected for other stressed hosts which may have a
			 * greater need for relief 
			 */
			if (estimatedCpuRelief >= cpuOverThreshold) {
				stressedHostsAddressed.add(host);
				simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).scaleUpRelief++;
				
				//reset stress window
				stressedHostWindow.remove(host);
				break;
			}
			
			for (Task task : scaleDownTasks) {
				
				for (TaskInstance instance : task.getInstances()) {
					if (instance.getVM().getVMAllocation().getHost() == host.getHost()) {
//						if (instance.getResourceScheduled().getCpu() > targetCpu &&
//								instance.getResourceScheduled().getCpu() >= cpuOverThreshold) {
						if (instance.getResourceScheduled().getCpu() > targetCpu) {
							targetInstance = instance;
							targetCpu = instance.getResourceScheduled().getCpu();
						}
					}
				}
			}
			
			if (targetInstance != null) {
				scaleDownTasks.remove(targetInstance.getTask());
				stressedHostsAddressed.add(host); //add to the list of hosts whose stress situation has been addressed
				RemoveTaskInstanceAction action = new RemoveTaskInstanceAction(targetInstance);
				action.execute(simulation, this);
				host.invalidateStatus(simulation.getSimulationTime());
				
				VmStatus vmToRemove = null;
				for (VmStatus vm : host.getSandboxStatus().getVms()) {
					if (vm.getId() == targetInstance.getVM().getId()) {
						vmToRemove = vm;
						break;
					}
				}
				host.getSandboxStatus().getVms().remove(vmToRemove);
				
				simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).scaleDownStress++;
				
//				System.out.println(SimTime.toHumanReadable(simulation.getSimulationTime()) + " Removing Instance from Task " + 
//						targetInstance.getTask().getApplication().getId() + "-" + targetInstance.getTask().getId() + 
//						" VM#" + targetInstance.getVM().getId() +
//						" on Host #" + host.getId());
				
				//reset stress window
				stressedHostWindow.remove(host);
				
			} else {				
				//if we have exceeded the stress window, trigger a migration (choose VM and target at a later point in the algorithm)
				if (stressedHostWindow.get(host) >= STRESS_WINDOW &&
						estimatedCpuRelief < cpuOverThreshold) {
					stressedPendingMigration.add(host);
						
					//remove from host window to prevent triggering more migrations until the next "migration window" (to match old policies)
					stressedHostWindow.remove(host);
				}
			}
		}
		
		//scale down other tasks located on hosts with utilization over target utilization
		ArrayList<Task> scaleTasks = new ArrayList<Task>();
		scaleTasks.addAll(scaleDownTasks);
		for (Task task : scaleTasks) {
			double targetHostUtil = -1;
			TaskInstance targetInstance = null;
			
			//choose a task instance to shut down (instance on host with highest utilization)
			for (TaskInstance instance : task.getInstances()) {
				double util = instance.getVM().getVMAllocation().getHost().getResourceManager().getCpuUtilization();
				
				if (util > targetHostUtil) {
					targetHostUtil = util;
					targetInstance = instance;
				}
			}
			
			if (targetHostUtil >= targetUtilization) { //only shutdown if over target utilization
				RemoveTaskInstanceAction action = new RemoveTaskInstanceAction(targetInstance);
				action.execute(simulation, this);
				hostPool.getHost(targetInstance.getVM().getVMAllocation().getHost().getId()).invalidateStatus(simulation.getSimulationTime());
				
				VmStatus vmToRemove = null;
				HostData targetHost = hostPool.getHost(targetInstance.getVM().getVMAllocation().getHost().getId());
				for (VmStatus vm : targetHost.getSandboxStatus().getVms()) {
					if (vm.getId() == targetInstance.getVM().getId()) {
						vmToRemove = vm;
						break;
					}
				}
				targetHost.getSandboxStatus().getVms().remove(vmToRemove);
				
				scaleDownTasks.remove(task);
				
				simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).scaleDown++;
				
//				System.out.println(SimTime.toHumanReadable(simulation.getSimulationTime()) + " Removing Instance from Task " + 
//						targetInstance.getTask().getApplication().getId() + "-" + targetInstance.getTask().getId() + 
//						" VM#" + targetInstance.getVM().getId() +
//						" on Host #" + targetInstance.getVM().getVMAllocation().getHost().getId());
			}
		}
		
		//attempt to place VMs from stressed hosts awaiting migration, first partially utilized, then underutilized with highest shutdown cost
		
		ArrayList<HostData> targets = new ArrayList<HostData>();
		// Sort Partially-Utilized hosts in increasing order by <CPU utilization, power efficiency>.
		Collections.sort(partiallyUtilized, HostDataComparator.getComparator(HostDataComparator.CPU_UTIL, HostDataComparator.EFFICIENCY));
		
		// Sort Underutilized hosts in decreasing order by <CPU utilization, power efficiency>.
		Collections.sort(underUtilized, HostDataComparator.getComparator(HostDataComparator.CPU_UTIL, HostDataComparator.EFFICIENCY));
		Collections.reverse(underUtilized);

		
		// Sort Empty hosts in decreasing order by <power efficiency, power state>.
		Collections.sort(empty, HostDataComparator.getComparator(HostDataComparator.EFFICIENCY, HostDataComparator.PWR_STATE));
		Collections.reverse(empty);
		
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		targets.addAll(empty);
		

		
		ArrayList<HostData> usedTargets = new ArrayList<HostData>();
		
		for (HostData source : stressedPendingMigration) {
			boolean found = false;
			ArrayList<VmStatus> vmList = new ArrayList<VmStatus>(); 
			vmList.addAll(orderSourceVms(source.getSandboxStatus().getVms(), source));
			for (VmStatus vm : vmList) {
				
				for (HostData target : targets) {
					// Check that target host has at most 1 incoming migration pending, 
					// that target host is capable and has enough capacity left to host the VM, 
					// and also that it will not exceed the target utilization.
					if (HostData.canHost(vm, target.getSandboxStatus(), target.getHostDescription()) && 
						(target.getSandboxStatus().getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
						
						// Modify host and vm states to record the future migration. Note that we 
						// can do this because we are using the designated 'sandbox' host status.
						source.getSandboxStatus().migrate(vm, target.getSandboxStatus());
						
						// Invalidate source and target status, as we know them to be incorrect until the next status update arrives.
						source.invalidateStatus(simulation.getSimulationTime());
						target.invalidateStatus(simulation.getSimulationTime());
						
						usedTargets.add(target);
						
						migrations.addAction(new MigrationAction(source.getHostManager(),
								source.getHost(),
								target.getHost(), 
								vm.getId()));
						
						simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).stressMigration++;
						
//						System.out.println(SimTime.toHumanReadable(simulation.getSimulationTime()) + " stress mig VM#" + vm.getId() + " from #" + source.getId() + " to #" + target.getId());
						
						found = true;
						break;
					}
				}
				
				if (found)
					break;
			}
		}

		//attempt to place VMs from scaling up applications into same target list
		for (Task task : scaleUpTasks) {
			VmAllocationRequest request = new VmAllocationRequest(new VmDescription(task));
			
			HostData allocatedHost = null;
			
			for (HostData target : targets) {
				Resources reqResources = new Resources();
				reqResources.setCpu(request.getCpu());
				reqResources.setMemory(request.getMemory());
				reqResources.setBandwidth(request.getBandwidth());
				reqResources.setStorage(request.getStorage());

				if (HostData.canHost(request.getVMDescription().getCores(), 	//target has capability and capacity to host VM 
						request.getVMDescription().getCoreCapacity(), 
						reqResources,
						target.getSandboxStatus(),
						target.getHostDescription()) &&
						(target.getHostDescription().getResourceCapacity().getCpu() - target.getSandboxStatus().getCpuAllocated()) >= request.getCpu()) //effectively disable overcommitting for initial placement
						{
					
					allocatedHost = target;
					
					//add a dummy placeholder VM to keep track of placed VM resource requirements
					target.getSandboxStatus().instantiateVm(
							new VmStatus(request.getVMDescription().getCores(),
									request.getVMDescription().getCoreCapacity(),
							reqResources));
					
					usedTargets.add(target);
					
					//invalidate this host status, as we know it to be incorrect until the next status update arrives
					target.invalidateStatus(simulation.getSimulationTime());
					
					simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).scaleUp++;
					
//					System.out.println(SimTime.toHumanReadable(simulation.getSimulationTime()) + " Adding Instance to Task " + 
//							task.getApplication().getId() + "-" + task.getId() + 
//							" on Host #" + target.getId());
					
					break;
				 }
			}
			
			if (allocatedHost != null) {
				//delay actions until placements have been found for all VMs
				InstantiateVmAction action = new InstantiateVmAction(allocatedHost, request, null);
				action.execute(simulation, this);
			} else {
				simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).instancePlacementsFailed++;
			}
		}
		scaleUpTasks.clear(); //all scaling up complete
		
		
		//calculate underutilized host shutdown cost (number of required migrations)
		HashMap<HostData, Integer> underUtilizedCost = new HashMap<HostData, Integer>();
		for (HostData host : underUtilized) {
			int cost = 0;
			for (Task task : scaleDownTasks) {
				for (TaskInstance instance : task.getInstances()) {
					if (instance.getVM().getVMAllocation().getHost().getId() == host.getId()) {
						++cost;
						break;
					}
				}
			}
			cost = host.getCurrentStatus().getVms().size() - cost; //cost = # of VMs - # of VMs that can be removed from down scaling = # of required migrations
			underUtilizedCost.put(host, cost);
		}
		
		//sort underutilized by increasing cost
		ArrayList<HostData> sortedUnderUtilized = new ArrayList<HostData>();
		ArrayList<HostData> unsortedUnderUtilized = new ArrayList<HostData>();
		unsortedUnderUtilized.addAll(underUtilized);
		while (unsortedUnderUtilized.size() > 0) {
			int lowestCost = Integer.MAX_VALUE;
			HostData lowestCostHost = null;
			for (HostData host : unsortedUnderUtilized) {
				if (underUtilizedCost.get(host) < lowestCost) {
					lowestCost = underUtilizedCost.get(host);
					lowestCostHost = host;
				}
			}
			sortedUnderUtilized.add(lowestCostHost);
			unsortedUnderUtilized.remove(lowestCostHost);
		}
		
		//attempt to shutdown underutilized hosts not chosen as targets
		ArrayList<HostData> usedSources = new ArrayList<HostData>();
		for (HostData source : sortedUnderUtilized) {
			if (!usedTargets.contains(source)) {	
				
				//check all remaining tasks requiring scale down, select instances on this host for shutdown
				ArrayList<Task> tempTasks = new ArrayList<Task>();
				tempTasks.addAll(scaleDownTasks);
				for (Task task : tempTasks) {
					for (TaskInstance instance : task.getInstances()) {
						if (instance.getVM().getVMAllocation().getHost().getId() == source.getId()) {
							scaleDownTasks.remove(task);
							RemoveTaskInstanceAction action = new RemoveTaskInstanceAction(instance);
							action.execute(simulation, this);
							source.invalidateStatus(simulation.getSimulationTime());
							
							VmStatus vmToRemove = null;
							for (VmStatus vm : source.getSandboxStatus().getVms()) {
								if (vm.getId() == instance.getVM().getId()) {
									vmToRemove = vm;
									break;
								}
							}
							source.getSandboxStatus().getVms().remove(vmToRemove);
							
							simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).scaleDownShutdown++;
							
//							System.out.println(SimTime.toHumanReadable(simulation.getSimulationTime()) + " Removing Instance from Task " + 
//									instance.getTask().getApplication().getId() + "-" + instance.getTask().getId() + 
//									" VM#" + instance.getVM().getId() +
//									" on Host #" + source.getId());
							break;
						}
					}
				}
				
				//migrate if underutilized window exceeded, migrate remaining VMs
				if (underutilHostWindow.get(source) >= UNDERUTIL_WINDOW) {
					simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).shutdownAttempts++;
					underutilHostWindow.remove(source);
					
//					ArrayList<VmStatus> vmList = this.orderSourceVms(source.getCurrentStatus().getVms());
					ArrayList<VmStatus> vmList = new ArrayList<VmStatus>(); 
					vmList.addAll(source.getSandboxStatus().getVms());
				
					for (VmStatus vm : vmList) {
						for (HostData target : targets) {
							// Check that source and target are different hosts, 
							// that target host hasn't been used as source, 
							// that target host is capable and has enough capacity left to host the VM, 
							// and also that it will not exceed the target utilization.
							if (source != target && 
									!usedSources.contains(target) && 
									HostData.canHost(vm, target.getSandboxStatus(), target.getHostDescription()) &&	
									(target.getSandboxStatus().getResourcesInUse().getCpu() + vm.getResourcesInUse().getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization) {
								
								// Modify host and vm states to record the future migration. Note that we 
								// can do this because we are using the designated 'sandbox' host status.
								source.getSandboxStatus().migrate(vm, target.getSandboxStatus());
								
								// Invalidate source and target status, as we know them to be incorrect until the next status update arrives.
								source.invalidateStatus(simulation.getSimulationTime());
								target.invalidateStatus(simulation.getSimulationTime());
								
								migrations.addAction(new MigrationAction(source.getHostManager(),
										source.getHost(),
										target.getHost(), 
										vm.getId()));
								
								simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).underMigration++;
								
//								System.out.println(SimTime.toHumanReadable(simulation.getSimulationTime()) + " underutil mig VM#" + vm.getId() + " from #" + source.getId() + " to #" + target.getId();
								
								// If the host will be empty after this migration, instruct it to shut down.
								if (source.getSandboxStatus().getVms().size() == 0) {
									shutdownActions.addAction(new ShutdownHostAction(source.getHost()));
									simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).shutdowns++;
								}
								
								usedTargets.add(target);
								usedSources.add(source);
								
								break;
							}
						}
					}
				}
				
				
			}
		}	
			
		//complete scale down actions for applications that have not had their scaling executed in previous steps
		for (Task task : scaleDownTasks) {
			double targetHostUtil = -1;
			TaskInstance targetInstance = null;
			
			//choose a task instance to shut down (instance on host with lowest utilization)
			for (TaskInstance instance : task.getInstances()) {
				double util = instance.getVM().getVMAllocation().getHost().getResourceManager().getCpuUtilization();
				
				if (util == 1)	System.out.println(simulation.getSimulationTime() + " " + util + " host#" + instance.getVM().getVMAllocation().getHost().getId());
				
				if (util > targetHostUtil) {
					targetHostUtil = util;
					targetInstance = instance;
				}
			}
			
			RemoveTaskInstanceAction action = new RemoveTaskInstanceAction(targetInstance);
			action.execute(simulation, this);
			hostPool.getHost(targetInstance.getVM().getVMAllocation().getHost().getId()).invalidateStatus(simulation.getSimulationTime());
			
			simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).scaleDown++;
			
//			System.out.println(SimTime.toHumanReadable(simulation.getSimulationTime()) + " Removing Instance from Task " + 
//					targetInstance.getTask().getApplication().getId() + "-" + targetInstance.getTask().getId() + 
//					" VM#" + targetInstance.getVM().getId() +
//					" on Host #" + targetInstance.getVM().getVMAllocation().getHost().getId());
		}
		
		//clean up any empty hosts not powered off (resulting from terminated instances or applications)
		for (HostData host : empty) {
			// Ensure that the host is not involved in any migrations and is not powering on.
			if (host.isStatusValid() && //indicates that no changes have been made to this host during current execution
				host.getCurrentStatus().getIncomingMigrationCount() == 0 && 
				host.getCurrentStatus().getOutgoingMigrationCount() == 0 && 
				host.getCurrentStatus().getStartingVmAllocations().size() == 0 &&
				host.getCurrentStatus().getState() != HostState.POWERING_ON &&
				host.getCurrentStatus().getState() != HostState.OFF &&
				host.getCurrentStatus().getState() != HostState.SUSPENDED) {
				
				host.invalidateStatus(simulation.getSimulationTime());
				shutdownActions.addAction(new ShutdownHostAction(host.getHost()));
				
				simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).emptyShutdown++;
			}
		}
		
		
		// Trigger actions
		SequentialManagementActionExecutor actionExecutor = new SequentialManagementActionExecutor();
		actionExecutor.addAction(migrations);
		actionExecutor.addAction(shutdownActions);
		actionExecutor.execute(simulation, this);
		
	}
	
	/**
	 * Receive and process Task Instance updates
	 * @param event
	 */
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
	
	/**
	 * Evaluate application scaling
	 * @param scaleUpTasks
	 * @param scaleDownTasks
	 * @param appPool
	 */
	private void evaluateScaling(ArrayList<Task> scaleUpTasks, ArrayList<Task> scaleDownTasks, ApplicationPoolManager appPool) {
		
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
				
				if (rt >= (sla.getResponseTime() * slaWarningThreshold) && regression.getSlope() > 0) {
					slaWarning = true;
				} else if (	rt < (sla.getResponseTime() * slaSafeThreshold ) && regression.getSlope() < 0) {
					slaSafe = true;
				}
			}
			
			if (slaWarning) {
	
				//Select Task to scale up
				Task targetTask = null;
				double targetIncrease = 0;
				
				//choose task which has had the fastest increase in response time
				for (Task task : app.getTasks()) {
					if (task.getInstances().size() < task.getMaxInstances()) {
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
						if ((utilization / (task.getInstances().size() - 1)) < cpuSafeThreshold) {
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
	
	private void updateHostWindows(ArrayList<HostData> stressed, ArrayList<HostData> underUtilized) {
		HashMap<HostData, Integer> newStressedHosts = new HashMap<HostData, Integer>();
		for (HostData host : stressed) {
			int val = 1;
			if (stressedHostWindow.containsKey(host)) {
				val += stressedHostWindow.get(host);
			}
			newStressedHosts.put(host, val);
		}
		stressedHostWindow = newStressedHosts;
		
		HashMap<HostData, Integer> newUnderutilHosts = new HashMap<HostData, Integer>();
		for (HostData host : underUtilized) {
			int val = 1;
			if (underutilHostWindow.containsKey(host)) {
				val += underutilHostWindow.get(host);
			}
			newUnderutilHosts.put(host, val);
		}
		underutilHostWindow = newUnderutilHosts;
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
