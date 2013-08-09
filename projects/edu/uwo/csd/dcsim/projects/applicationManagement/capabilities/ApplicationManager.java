package edu.uwo.csd.dcsim.projects.applicationManagement.capabilities;

import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.management.capabilities.ManagerCapability;

public class ApplicationManager extends ManagerCapability {

	private Application application;
	
	public ApplicationManager(Application application) {
		this.application = application;
	}

	public Application getApplication() {
		return application;
	}

	public void setApplication(Application application) {
		this.application = application;
	}
	
}
