package edu.uwo.csd.dcsim.projects.applicationManagement.events;

import edu.uwo.csd.dcsim.application.Application;
import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.management.AutonomicManager;

public class ShutdownApplicationEvent extends Event {

	private Application application;
	
	public ShutdownApplicationEvent(AutonomicManager target, Application application) {
		super(target);
		this.application = application;
	}

	public Application getApplication() {
		return application;
	}

	public void setApplication(Application application) {
		this.application = application;
	}

}
