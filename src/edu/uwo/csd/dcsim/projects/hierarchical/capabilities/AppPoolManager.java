package edu.uwo.csd.dcsim.projects.hierarchical.capabilities;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.uwo.csd.dcsim.management.capabilities.ManagerCapability;
import edu.uwo.csd.dcsim.projects.hierarchical.AppData;

/**
 * @author Gaston Keller
 *
 */
public class AppPoolManager extends ManagerCapability {
	
	private Map<Integer, AppData> applicationMap = new HashMap<Integer, AppData>();
	
	public void addApplication(AppData application) {
		applicationMap.put(application.getId(), application);
	}
	
	public Collection<AppData> getApplications() {
		return applicationMap.values();
	}
	
	public AppData getApplication(int id) {
		return applicationMap.get(id);
	}

}
