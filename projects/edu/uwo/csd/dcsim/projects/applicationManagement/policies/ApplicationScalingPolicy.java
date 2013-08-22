package edu.uwo.csd.dcsim.projects.applicationManagement.policies;

import edu.uwo.csd.dcsim.application.*;
import edu.uwo.csd.dcsim.application.sla.InteractiveServiceLevelAgreement;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.projects.applicationManagement.ApplicationManagementMetrics;
import edu.uwo.csd.dcsim.projects.applicationManagement.actions.AddTaskInstanceAction;
import edu.uwo.csd.dcsim.projects.applicationManagement.actions.RemoveTaskInstanceAction;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.ApplicationManager;

public class ApplicationScalingPolicy extends Policy {

	private static final double SLA_WARNING_THRESHOLD = 0.8;
	private static final double SLA_SAFE_THRESHOLD = 0.5;
	
	private AutonomicManager dcManager;
	
	public ApplicationScalingPolicy(AutonomicManager dcManager) {
		addRequiredCapability(ApplicationManager.class);
		this.dcManager = dcManager;
	}
	
	public void execute() {
		
		ApplicationManager appManager = manager.getCapability(ApplicationManager.class);
		Application app = appManager.getApplication();
		
		//grey box
		boolean slaWarning = false;
		boolean slaSafe = false;
		if (app instanceof InteractiveApplication && app.getSla() instanceof InteractiveServiceLevelAgreement) {
			InteractiveApplication interactiveApp = (InteractiveApplication)app;
			InteractiveServiceLevelAgreement sla = (InteractiveServiceLevelAgreement)app.getSla();
			if (interactiveApp.getResponseTime() >= (sla.getResponseTime() * SLA_WARNING_THRESHOLD)) {
				slaWarning = true;
			} else if (	interactiveApp.getResponseTime() < (sla.getResponseTime() * SLA_SAFE_THRESHOLD)) {
				slaSafe = true;
			}
		}
		
		if (slaWarning || !app.getSla().evaluate()) {

			//add an Instance to every Task
			for (Task task : app.getTasks()) {
				if (task.getInstances().size() < task.getMaxInstances()) {
					AddTaskInstanceAction action = new AddTaskInstanceAction(task, dcManager);
					action.execute(simulation, this);
					simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).instancesAdded++;
					
//					System.out.println("Adding Instance to Task " + app.getId() + "-" + task.getId());
				}
			}
			
//			//add an Instance to the Task with highest utilization
//			double utilization = -1;
//			Task targetTask = null;
//			
//			for (Task task : app.getTasks()) {
//				if (task.getInstances().size() < task.getMaxInstances()) {
//					double taskUtil = 0;
//					for (TaskInstance instance : task.getInstances()) {
//						taskUtil += instance.getResourceScheduled().getCpu() / task.getResourceSize().getCpu();
//					}
//					taskUtil = taskUtil / task.getInstances().size();
//					
//					if (taskUtil > utilization) {
//						utilization = taskUtil;
//						targetTask = task;
//					}
//				}
//			}
//			
//			if (targetTask != null) {
//				AddTaskInstanceAction action = new AddTaskInstanceAction(targetTask, dcManager);
//				action.execute(simulation, this);
//				simulation.getSimulationMetrics().getCustomMetricCollection(ApplicationManagementMetrics.class).instancesAdded++;
//				
//				System.out.println("Adding Instance to Task " + app.getId() + "-" + targetTask.getId());
//			}
			
		} else if (slaSafe) {
			//look for an underutilized task to scale down (choose lowest utilization task)
			Task targetTask = null;
			double targetUtil = Double.MAX_VALUE;
			
			for (Task task : app.getTasks()) {
				if (task.getInstances().size() > 1) {
					double utilization = 0;
					for (TaskInstance instance : task.getInstances()) {
						utilization += instance.getResourceScheduled().getCpu() / (double)task.getResourceSize().getCpu();
					}
					utilization = utilization / task.getInstances().size();

					if (((utilization / (task.getInstances().size() - 1)) + utilization) < 0.5) {
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
				
//				System.out.println("Removing Instance from Task " + app.getId() + "-" + targetTask.getId());
				
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
