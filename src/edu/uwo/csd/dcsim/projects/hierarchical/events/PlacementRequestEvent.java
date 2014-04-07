package edu.uwo.csd.dcsim.projects.hierarchical.events;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.application.InteractiveTask;
import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;
import edu.uwo.csd.dcsim.vm.VmDescription;

public class PlacementRequestEvent extends Event {

	private InteractiveApplication application;
	private boolean failed = false;
	
	private ArrayList<VmAllocationRequest> independentVms;
	private ArrayList<ArrayList<VmAllocationRequest>> antiAffinityVms;
	private ArrayList<ArrayList<VmAllocationRequest>> affinityVms;
	
	public PlacementRequestEvent(AutonomicManager target, InteractiveApplication application) {
		super(target);
		
		this.application = application;
		
		// Generate constrain sets.
		independentVms = new ArrayList<VmAllocationRequest>();
		for (InteractiveTask task : application.getIndependentTasks()) {
			independentVms.add(new VmAllocationRequest(new VmDescription(task)));
		}
		
		antiAffinityVms = new ArrayList<ArrayList<VmAllocationRequest>>();
		for (InteractiveTask task : application.getAntiAffinityTasks()) {
			antiAffinityVms.add(task.createInitialVmRequests());
		}
		
		affinityVms = new ArrayList<ArrayList<VmAllocationRequest>>();
		for (ArrayList<InteractiveTask> set : application.getAffinityTasks()) {
			ArrayList<VmAllocationRequest> vms = new ArrayList<VmAllocationRequest>();
			for (InteractiveTask task : set) {
				vms.add(new VmAllocationRequest(new VmDescription(task)));
			}
			affinityVms.add(vms);
		}
	}
	
	public InteractiveApplication getApplication() {
		return application;
	}
	
	public void setFailed(boolean failed) {
		this.failed = failed;
	}
	
	public boolean isFailed() {
		return failed;
	}
	
	public ArrayList<VmAllocationRequest> getIndependentVms() {
		return independentVms;
	}
	
	public ArrayList<ArrayList<VmAllocationRequest>> getAntiAffinityVms() {
		return antiAffinityVms;
	}
	
	public ArrayList<ArrayList<VmAllocationRequest>> getAffinityVms() {
		return affinityVms;
	}

}
