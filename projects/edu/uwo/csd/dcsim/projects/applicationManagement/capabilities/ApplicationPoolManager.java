package edu.uwo.csd.dcsim.projects.applicationManagement.capabilities;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.application.TaskInstance;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.capabilities.ManagerCapability;

public class ApplicationPoolManager extends ManagerCapability {

	private HashMap<Application, ApplicationData> applicationData = new HashMap<Application, ApplicationData>();
	private int windowSize;
	private int longWindowSize;
	
	public ApplicationPoolManager(int windowSize, int longWindowSize) {
		this.windowSize = windowSize;
		this.longWindowSize = longWindowSize;
	}
	
	public ApplicationData getApplicationData(Application application) {
		return applicationData.get(application);
	}
	
	public void addApplication(Application application) {
		applicationData.put(application, new ApplicationData(application));
	}
	
	public void removeApplication(Application application) {
		applicationData.remove(application);
	}
	
	public Map<Application, ApplicationData> getApplicationData() {
		return applicationData;
	}
	
	public class ApplicationData {
		private Application application;
		private DescriptiveStatistics applicationResponseTimes;
		private Map<TaskInstance, AutonomicManager> instanceManagers = new HashMap<TaskInstance, AutonomicManager>();
		private Map<TaskInstance, DescriptiveStatistics> instanceCpuUtils = new HashMap<TaskInstance, DescriptiveStatistics>();
		private Map<TaskInstance, DescriptiveStatistics> instanceResponseTimes = new HashMap<TaskInstance, DescriptiveStatistics>();
		
		private DescriptiveStatistics applicationResponseTimesLong;
		private Map<TaskInstance, DescriptiveStatistics> instanceCpuUtilsLong = new HashMap<TaskInstance, DescriptiveStatistics>();
		private Map<TaskInstance, DescriptiveStatistics> instanceResponseTimesLong = new HashMap<TaskInstance, DescriptiveStatistics>();
		
		private long lastScaleUp = Long.MIN_VALUE;
		
		public ApplicationData (Application application) {
			this.application = application;
			
			applicationResponseTimes = new DescriptiveStatistics(windowSize);
			applicationResponseTimesLong = new DescriptiveStatistics(longWindowSize);
		}
		
		public Application getApplication() {
			return application;
		}
		
		public long getLastScaleUp() {
			return lastScaleUp;
		}
		
		public void setLastScaleUp(long lastScaleUp) {
			this.lastScaleUp = lastScaleUp;
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
	
}
