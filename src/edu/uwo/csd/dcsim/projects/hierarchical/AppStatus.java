package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.ArrayList;
import java.util.Map;

import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.application.InteractiveTask;
import edu.uwo.csd.dcsim.application.TaskInstance;
import edu.uwo.csd.dcsim.management.VmStatus;

/**
 * Represents the current status of an application. It contains a VmStatus object for each VM that
 * hosts a Task instance of the application. The VmStatus objects are arranged in accordance with
 * the task constraints defined for the application.
 * 
 * @author Gaston Keller
 *
 */
public class AppStatus {

	private int id = -1;
	//private InteractiveApplication application;
	private ArrayList<VmStatus> independentVms;
	private ArrayList<ArrayList<VmStatus>> antiAffinityVms;
	private ArrayList<ArrayList<VmStatus>> affinityVms;
	
	@Deprecated
	public AppStatus(InteractiveApplication application) {
		id = application.getId();
//		this.application = application;
		
		// Generate constrain sets.
		independentVms = new ArrayList<VmStatus>();
		for (InteractiveTask task : application.getIndependentTasks()) {
			independentVms.add(new VmStatus(task.getInstances().get(0).getVM(), 0));
		}
		
		antiAffinityVms = new ArrayList<ArrayList<VmStatus>>();
		for (InteractiveTask task : application.getAntiAffinityTasks()) {
			ArrayList<VmStatus> instances = new ArrayList<VmStatus>();
			for (TaskInstance instance : task.getInstances()) {
				instances.add(new VmStatus(instance.getVM(), 0));
			}
			antiAffinityVms.add(instances);
		}
		
		affinityVms = new ArrayList<ArrayList<VmStatus>>();
		for (ArrayList<InteractiveTask> set : application.getAffinityTasks()) {
			ArrayList<VmStatus> vms = new ArrayList<VmStatus>();
			for (InteractiveTask task : set) {
				vms.add(new VmStatus(task.getInstances().get(0).getVM(), 0));
			}
			affinityVms.add(vms);
		}
	}
	
	public AppStatus(AppData application, Map<Integer, VmStatus> vms) {
		id = application.getId();
		
		// Generate constrain sets.
		independentVms = new ArrayList<VmStatus>();
		for (InteractiveTask task : application.getIndependentTasks()) {
			independentVms.add(vms.get(application.getTask(task.getId()).getHostingVm()));
		}
		
		antiAffinityVms = new ArrayList<ArrayList<VmStatus>>();
		for (InteractiveTask task : application.getAntiAffinityTasks()) {
			ArrayList<VmStatus> instances = new ArrayList<VmStatus>();
			for (int vmId : application.getTask(task.getId()).getHostingVms()) {
				instances.add(vms.get(vmId));
			}
			antiAffinityVms.add(instances);
		}
		
		affinityVms = new ArrayList<ArrayList<VmStatus>>();
		for (ArrayList<InteractiveTask> set : application.getAffinityTasks()) {
			ArrayList<VmStatus> vmSet = new ArrayList<VmStatus>();
			for (InteractiveTask task : set) {
				vmSet.add(vms.get(application.getTask(task.getId()).getHostingVm()));
			}
			affinityVms.add(vmSet);
		}
	}
	
	public int getId() {
		return id;
	}
	
//	public InteractiveApplication getApplication() {
//		return application;
//	}
	
	public ArrayList<VmStatus> getIndependentVms() {
		return independentVms;
	}
	
	public ArrayList<ArrayList<VmStatus>> getAntiAffinityVms() {
		return antiAffinityVms;
	}
	
	public ArrayList<ArrayList<VmStatus>> getAffinityVms() {
		return affinityVms;
	}
	
	public ArrayList<VmStatus> getAllVms() {
		ArrayList<VmStatus> vms = new ArrayList<VmStatus>(independentVms);
		
		for (ArrayList<VmStatus> antiAffinitySet : antiAffinityVms) {
			vms.addAll(antiAffinitySet);
		}
		
		for (ArrayList<VmStatus> affinitySet : affinityVms) {
			vms.addAll(affinitySet);
		}
		
		return vms;
	}

}
