package edu.uwo.csd.dcsim.projects.applicationManagement.capabilities;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.application.TaskInstance;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.capabilities.ManagerCapability;

public class ApplicationManager extends ManagerCapability {

	private Application application;
	private DescriptiveStatistics applicationResponseTimes;
	private Map<TaskInstance, AutonomicManager> instanceManagers = new HashMap<TaskInstance, AutonomicManager>();
	private Map<TaskInstance, DescriptiveStatistics> instanceCpuUtils = new HashMap<TaskInstance, DescriptiveStatistics>();
	private Map<TaskInstance, DescriptiveStatistics> instanceResponseTimes = new HashMap<TaskInstance, DescriptiveStatistics>();
	
	private DescriptiveStatistics applicationResponseTimesLong;
	private Map<TaskInstance, DescriptiveStatistics> instanceCpuUtilsLong = new HashMap<TaskInstance, DescriptiveStatistics>();
	private Map<TaskInstance, DescriptiveStatistics> instanceResponseTimesLong = new HashMap<TaskInstance, DescriptiveStatistics>();
	
	private int windowSize;
	private int longWindowSize;
	
	public ApplicationManager(Application application, int windowSize, int longWindowSize) {
		this.application = application;
		
		this.windowSize = windowSize;
		this.longWindowSize = longWindowSize;
		applicationResponseTimes = new DescriptiveStatistics(windowSize);
		applicationResponseTimesLong = new DescriptiveStatistics(longWindowSize);
	}

	public Application getApplication() {
		return application;
	}

	public void setApplication(Application application) {
		this.application = application;
	}
	
	public DescriptiveStatistics getApplicationResponseTimes() {
		return applicationResponseTimes;
	}
	
	public DescriptiveStatistics getApplicationResponseTimesLong() {
		return applicationResponseTimesLong;
	}
	
	public Map<TaskInstance, AutonomicManager> getInstanceManagers() {
		return instanceManagers;
	}
	
	public void addInstanceManager(TaskInstance taskInstance, AutonomicManager instanceManager) {
		instanceManagers.put(taskInstance, instanceManager);
		instanceCpuUtils.put(taskInstance, new DescriptiveStatistics(windowSize));
		instanceResponseTimes.put(taskInstance, new DescriptiveStatistics(windowSize));
		
		instanceCpuUtilsLong.put(taskInstance, new DescriptiveStatistics(longWindowSize));
		instanceResponseTimesLong.put(taskInstance, new DescriptiveStatistics(longWindowSize));
	}
	
	public void removeInstanceManager(TaskInstance taskInstance) {
		instanceManagers.remove(taskInstance);
		instanceCpuUtils.remove(taskInstance);
		instanceResponseTimes.remove(taskInstance);
		
		instanceCpuUtilsLong.remove(taskInstance);
		instanceResponseTimesLong.remove(taskInstance);
	}
	
	public Map<TaskInstance, DescriptiveStatistics> getInstanceCpuUtils() {
		return instanceCpuUtils;
	}
	
	public Map<TaskInstance, DescriptiveStatistics> getInstanceResponseTimes() {
		return instanceResponseTimes;
	}
	
	public Map<TaskInstance, DescriptiveStatistics> getInstanceCpuUtilsLong() {
		return instanceCpuUtilsLong;
	}
	
	public Map<TaskInstance, DescriptiveStatistics> getInstanceResponseTimesLong() {
		return instanceResponseTimesLong;
	}
	
}