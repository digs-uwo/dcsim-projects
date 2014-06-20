package edu.uwo.csd.dcsim.projects.hierarchical;

import edu.uwo.csd.dcsim.common.HashCodeUtil;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.VmStatus;

public class VmData {
	
	private int id = -1;
	private TaskInstanceData task;
	private HostData host;
	
	private VmStatus status;
	
	private final int hashCode;
	
	public VmData(int vmId, TaskInstanceData taskInstance, HostData host) {
		this.id = vmId;
		this.task = taskInstance;
		this.host = host;
		
		// init hashCode
		hashCode = generateHashCode();
	}
	
	public int getId() {
		return id;
	}
	
	public TaskInstanceData getTask() {
		return task;
	}
	
	public HostData getHost() {
		return host;
	}
	
	public void updateHost(HostData host) {
		this.host = host;
	}
	
	public VmStatus getCurrentStatus() {
		return status;
	}
	
	public void setCurrentStatus(VmStatus status) {
		this.status = status;
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
