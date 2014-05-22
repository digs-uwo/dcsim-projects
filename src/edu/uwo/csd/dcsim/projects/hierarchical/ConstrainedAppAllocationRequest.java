package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.application.InteractiveTask;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;
import edu.uwo.csd.dcsim.vm.VmDescription;

/**
 * An allocation request for an application with constraints.
 * 
 * @author Gaston Keller
 *
 */
public class ConstrainedAppAllocationRequest {

	private int id = -1;
	private InteractiveApplication application;
	private ArrayList<VmAllocationRequest> independentVms;
	private ArrayList<ArrayList<VmAllocationRequest>> antiAffinityVms;
	private ArrayList<ArrayList<VmAllocationRequest>> affinityVms;
	
	public ConstrainedAppAllocationRequest(InteractiveApplication application) {
		id = application.getId();
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
	
	public int getId() {
		return id;
	}
	
	public InteractiveApplication getApplication() {
		return application;
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
	
	public ArrayList<VmAllocationRequest> getAllVmAllocationRequests() {
		ArrayList<VmAllocationRequest> vms = new ArrayList<VmAllocationRequest>(independentVms);
		
		for (ArrayList<VmAllocationRequest> antiAffinitySet : antiAffinityVms) {
			vms.addAll(antiAffinitySet);
		}
		
		for (ArrayList<VmAllocationRequest> affinitySet : affinityVms) {
			vms.addAll(affinitySet);
		}
		
		return vms;
	}

}
