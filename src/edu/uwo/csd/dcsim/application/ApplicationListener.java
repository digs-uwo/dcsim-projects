package edu.uwo.csd.dcsim.application;

public interface ApplicationListener {

	public void onCreateTaskInstance(TaskInstance taskInstance);
	public void onRemoveTaskInstance(TaskInstance taskInstance);
	
}
