package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import edu.uwo.csd.dcsim.application.InteractiveApplication;
import edu.uwo.csd.dcsim.application.InteractiveTask;
import edu.uwo.csd.dcsim.application.Task;
import edu.uwo.csd.dcsim.common.HashCodeUtil;
import edu.uwo.csd.dcsim.management.AutonomicManager;

/**
 * @author Gaston Keller
 *
 */
public class AppData {

	private int id = -1;
	
	private boolean isMaster = true;
	private AutonomicManager master = null;
	private HashSet<AutonomicManager> surrogates = null;
	
	private HashMap<Integer, TaskData> tasks;
	
	// Tasks arranged by their constraints.
	private ArrayList<InteractiveTask> independentTasks;
	private ArrayList<InteractiveTask> antiAffinityTasks;
	private ArrayList<ArrayList<InteractiveTask>> affinityTasks;
	
	private final int hashCode;
	
	public AppData(InteractiveApplication application, AutonomicManager localManager) {
		id = application.getId();
		master = localManager;
		
		tasks = new HashMap<Integer, TaskData>();
		for (Task task : application.getTasks()) {
			tasks.put(task.getId(), new TaskData(task, id));
		}
		
		independentTasks = application.getIndependentTasks();
		antiAffinityTasks = application.getAntiAffinityTasks();
		affinityTasks = application.getAffinityTasks();
		
		// init hashCode
		hashCode = generateHashCode();
	}
	
	private AppData(AppData original) {
		id = original.id;
		isMaster = original.isMaster;
		master = original.master;
		if (null != surrogates)
			surrogates = new HashSet<AutonomicManager>(original.surrogates);
		tasks = new HashMap<Integer, TaskData>(original.tasks);
		independentTasks = new ArrayList<InteractiveTask>(original.independentTasks);
		antiAffinityTasks = new ArrayList<InteractiveTask>(original.antiAffinityTasks);
		affinityTasks = new ArrayList<ArrayList<InteractiveTask>>(original.affinityTasks);
		hashCode = original.hashCode;
	}
	
	public AppData createSurrogate(TaskInstanceData instance, AutonomicManager remoteManager) {
		AppData app = new AppData(this);
		
		app.isMaster = false;
		app.surrogates = null;
		
		// Move given task to surrogate application, or create a copy of the task (if the task has multiple instances).
		TaskData task = tasks.get(instance.getTaskId());
		if (task.getInstances().size() == 1) {		// Task has only one instance; move whole task from master to surrogate application.
			tasks.remove(task.getId());
			app.tasks = new HashMap<Integer, TaskData>();
			app.tasks.put(task.getId(), task);
		}
		else {		// Task has multiple instances; remove instance from task, and create a copy of the task containing only the given instance.
			task.removeInstance(instance.getId());
			TaskData remoteTask = task.copy();
			remoteTask.getInstances().clear();
			remoteTask.addInstance(instance);
		}
		
		// Add target manager to the list of surrogate holders.
		if (null == surrogates)
			surrogates = new HashSet<AutonomicManager>();
		surrogates.add(remoteManager);
		
		return app;
	}
	
	public void mergeSurrogate(AppData surrogate, AutonomicManager remoteManager) {
		
		if (id != surrogate.getId())
			throw new RuntimeException(String.format("[AppData] Trying to merge two different applications: App #%d and #%d.", id, surrogate.getId()));
		if (surrogate.isMaster())
			throw new RuntimeException(String.format("[AppData] Surrogate application submitted for merging is actually a master!"));
		
		// Add back surrogate's tasks.
		for (TaskData task : surrogate.getTasks()) {
			TaskData localTask = tasks.get(task.getId());
			if (null != localTask)
				for (TaskInstanceData instance : task.getInstances())
					localTask.addInstance(instance);
			else
				tasks.put(task.getId(), task);
		}
		
		// If this is the master application, remove the remote manager from the list of surrogate holders.
		if (isMaster) {
			surrogates.remove(remoteManager);
			if (surrogates.isEmpty())
				surrogates = null;
		}
	}
	
	public int getId() {
		return id;
	}
	
	public boolean isMaster() {
		return isMaster;
	}
	
	public AutonomicManager getMaster() {
		return master;
	}
	
	public ArrayList<AutonomicManager> getSurrogates() {
		if (null == surrogates)
			return null;
		else { 
			ArrayList<AutonomicManager> out = new ArrayList<AutonomicManager>();
			
			Iterator<AutonomicManager> it = surrogates.iterator();
			while (it.hasNext())
				out.add(it.next());
			
			return out;
		}
	}
	
	public TaskData getTask(int taskId) {
		return tasks.get(taskId);
	}
	
	public Collection<TaskData> getTasks() {
		return tasks.values();
	}
	
	public ArrayList<InteractiveTask> getAffinitySet(int taskId) {
		
		for (ArrayList<InteractiveTask> affinitySet : affinityTasks) {
			for (InteractiveTask t : affinitySet) {
				if (t.getId() == taskId)
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
			for (TaskInstanceData instance : task.getInstances()) {
				vms.add(instance.getHostingVmId());
			}
		}
		
		return vms;
	}
	
	/**
	 * Returns the current number of task instances in execution.
	 */
	public int size() {
		int count = 0;
		for (TaskData task : tasks.values()) {
			count += task.getInstances().size();
		}
		return count;
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
