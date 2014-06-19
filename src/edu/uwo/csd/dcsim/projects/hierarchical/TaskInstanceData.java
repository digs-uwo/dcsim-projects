package edu.uwo.csd.dcsim.projects.hierarchical;

import edu.uwo.csd.dcsim.common.HashCodeUtil;

/**
 * @author Gaston Keller
 *
 */
public class TaskInstanceData {

	private long id = -1;
	private int taskId = -1;
	private int appId = -1;
	private int vmId = -1;		// Id of the VM hosting the task instance.
	
	private final int hashCode;
	
	public TaskInstanceData(long id, int taskId, int appId) {
		this.id = id;
		this.taskId = taskId;
		this.appId = appId;
		
		// init hashCode
		hashCode = generateHashCode();
	}
	
	public long getId() {
		return id;
	}
	
	public int getTaskId() {
		return taskId;
	}
	
	public int getAppId() {
		return appId;
	}
	
	public int getHostingVmId() {
		return vmId;
	}
	
	public void setHostingVm(int vmId) {
		this.vmId = vmId;
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
