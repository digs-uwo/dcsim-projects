package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.application.InteractiveTask;
import edu.uwo.csd.dcsim.application.Task;
import edu.uwo.csd.dcsim.application.Task.TaskConstraintType;
import edu.uwo.csd.dcsim.common.HashCodeUtil;

/**
 * @author Gaston Keller
 *
 */
public class AppData {

	private int id = -1;
	
	private boolean isPrimary = true;
	private AppData primary = null;
	private ArrayList<AppData> secondaries = null;
	
	private HashMap<Integer, TaskData> tasks;
	
	// Tasks arranged by their constraints.
	private ArrayList<InteractiveTask> independentTasks;
	private ArrayList<InteractiveTask> antiAffinityTasks;
	private ArrayList<ArrayList<InteractiveTask>> affinityTasks;
	
	private final int hashCode;
	
	public AppData(InteractiveApplication application) {
		id = application.getId();
		
		tasks = new HashMap<Integer, TaskData>();
		for (Task task : application.getTasks()) {
			tasks.put(task.getId(), new TaskData(task, this));
		}
		
		independentTasks = application.getIndependentTasks();
		antiAffinityTasks = application.getAntiAffinityTasks();
		affinityTasks = application.getAffinityTasks();
		
		// init hashCode
		hashCode = generateHashCode();
	}
	
	private AppData(AppData original) {
		id = original.id;
		tasks = new HashMap<Integer, TaskData>(original.tasks);
		independentTasks = new ArrayList<InteractiveTask>(original.independentTasks);
		antiAffinityTasks = new ArrayList<InteractiveTask>(original.antiAffinityTasks);
		affinityTasks = new ArrayList<ArrayList<InteractiveTask>>(original.affinityTasks);
		hashCode = original.hashCode;
	}
	
	public AppData createSecondary() {
		AppData app = new AppData(this);
		
		app.isPrimary = false;
		app.primary = this;
		if (null == secondaries)
			secondaries = new ArrayList<AppData>();
		secondaries.add(app);
		
		return app;
	}
	
	public int getId() {
		return id;
	}
	
	public boolean isPrimary() {
		return isPrimary;
	}
	
	public AppData getPrimary() {
		return primary;
	}
	
	public ArrayList<AppData> getSecondaries() {
		return secondaries;
	}
	
	public TaskData getTask(int taskId) {
		return tasks.get(taskId);
	}
	
	public Collection<TaskData> getTasks() {
		return tasks.values();
	}
	
	public ArrayList<InteractiveTask> getAffinitySet(TaskData task) {
		
		for (ArrayList<InteractiveTask> affinitySet : affinityTasks) {
			for (InteractiveTask t : affinitySet) {
				if (t.getId() == task.getId())
					return new ArrayList<InteractiveTask>(affinitySet);
			}
		}
		
		return null;
	}
	
	public ArrayList<ArrayList<InteractiveTask>> getAffinityTasks() {
		return new ArrayList<ArrayList<InteractiveTask>>(affinityTasks);
	}
	
	public ArrayList<InteractiveTask> getAntiAffinityTasks() {
		return new ArrayList<InteractiveTask>(antiAffinityTasks);
	}
	
	public ArrayList<InteractiveTask> getIndependentTasks() {
		return new ArrayList<InteractiveTask>(independentTasks);
	}
	
	public ArrayList<Integer> getHostingVmsIds() {
		ArrayList<Integer> vms = new ArrayList<Integer>();
		for (TaskData task : tasks.values()) {
			if (task.getConstraintType() == TaskConstraintType.ANTI_AFFINITY) {
				vms.addAll(task.getHostingVms());
			}
			else
				vms.add(task.getHostingVm());
		}
		
		return vms;
	}
	
	@Override
	public int hashCode() {
		return hashCode;
	}
	
	private int generateHashCode() {
		int result = HashCodeUtil.SEED;
		result = HashCodeUtil.hash(result, id);
		return result;
	}

}