package edu.uwo.csd.dcsim.application;

/**
 * @author Michael Tighe
 *
 */
public class VmmTaskInstance extends TaskInstance {

	private VmmTask task;
	
	public VmmTaskInstance(VmmTask task) {
		this.task = task;
	}

	@Override
	public void postScheduling() {
	
	}

	@Override
	public Task getTask() {
		return task;
	}

}
