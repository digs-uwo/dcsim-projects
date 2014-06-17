package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.application.Task;
import edu.uwo.csd.dcsim.application.Task.TaskConstraintType;
import edu.uwo.csd.dcsim.common.HashCodeUtil;

/**
 * @author Gaston Keller
 *
 */
public class TaskData {

	private int id = -1;
	@Deprecated
	private AppData application;
	private int appId = -1;
	private TaskConstraintType constraintType;
	private int hostingVmId = -1;
	private ArrayList<Integer> hostingVmsIds = null;
	
	private final int hashCode;
	
	@Deprecated
	public TaskData(Task task, AppData application) {
		id = task.getId();
		this.application = application;
		constraintType = task.getConstraintType();
		if (constraintType == TaskConstraintType.ANTI_AFFINITY) {
			hostingVmsIds = new ArrayList<Integer>();
		}
		
		// init hashCode
		hashCode = generateHashCode();
	}
	
	public TaskData(Task task, int appId) {
		id = task.getId();
		this.appId = appId;
		constraintType = task.getConstraintType();
		if (constraintType == TaskConstraintType.ANTI_AFFINITY) {
			hostingVmsIds = new ArrayList<Integer>();
		}
		
		// init hashCode
		hashCode = generateHashCode();
	}
	
	public int getId() {
		return id;
	}
	
	public int getAppId() {
		return appId;
	}
	
	@Deprecated
	public AppData getApplication() {
		return application;
	}
	
	public TaskConstraintType getConstraintType() {
		return constraintType;
	}
	
	public int getHostingVm() {
		return hostingVmId;
	}
	
	public ArrayList<Integer> getHostingVms() {
		return hostingVmsIds;
	}
	
	public void setHostingVm(int vmId) {
		if (constraintType == TaskConstraintType.ANTI_AFFINITY) {
			hostingVmsIds.add(vmId);
		}
		else
			hostingVmId = vmId;
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
