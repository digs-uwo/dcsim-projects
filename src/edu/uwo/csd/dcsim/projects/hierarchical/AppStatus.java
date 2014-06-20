package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.ArrayList;
import java.util.Map;

import edu.uwo.csd.dcsim.application.InteractiveTask;
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
	private ArrayList<VmStatus> independentVms;
	private ArrayList<ArrayList<VmStatus>> antiAffinityVms;
	private ArrayList<ArrayList<VmStatus>> affinityVms;
	
	public AppStatus(AppData application, Map<Integer, VmStatus> vms) {
		id = application.getId();
		
		// Generate constrain sets.
		independentVms = new ArrayList<VmStatus>();
		for (InteractiveTask task : application.getIndependentTasks()) {
			independentVms.add(vms.get(application.getTask(task.getId()).getInstance().getHostingVmId()));
		}
		
		antiAffinityVms = new ArrayList<ArrayList<VmStatus>>();
		for (InteractiveTask task : application.getAntiAffinityTasks()) {
			ArrayList<VmStatus> instances = new ArrayList<VmStatus>();
			for (TaskInstanceData instance : application.getTask(task.getId()).getInstances()) {
				instances.add(vms.get(instance.getHostingVmId()));
			}
			antiAffinityVms.add(instances);
		}
		
		affinityVms = new ArrayList<ArrayList<VmStatus>>();
		for (ArrayList<InteractiveTask> set : application.getAffinityTasks()) {
			ArrayList<VmStatus> vmSet = new ArrayList<VmStatus>();
			for (InteractiveTask task : set) {
				vmSet.add(vms.get(application.getTask(task.getId()).getInstance().getHostingVmId()));
			}
			affinityVms.add(vmSet);
		}
	}
	
	public int getId() {
		return id;
	}
	
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
