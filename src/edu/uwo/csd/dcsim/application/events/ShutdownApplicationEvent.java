package edu.uwo.csd.dcsim.application.events;

import edu.uwo.csd.dcsim.application.*;
import edu.uwo.csd.dcsim.core.Event;

public class ShutdownApplicationEvent extends Event {

	private Application service;
	
	public ShutdownApplicationEvent(ApplicationGenerator serviceProducer, Application service) {
		super(serviceProducer);
		this.service = service;
	}
	
	public Application getService() {
		return service;
	}

}
