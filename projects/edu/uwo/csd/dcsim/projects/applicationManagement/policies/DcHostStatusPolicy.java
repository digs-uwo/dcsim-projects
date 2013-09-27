package edu.uwo.csd.dcsim.projects.applicationManagement.policies;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.management.events.HostStatusEvent;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.DataCentreManager;

public class DcHostStatusPolicy extends Policy {

	ArrayList<Class<? extends Event>> triggerEvents = new ArrayList<Class<? extends Event>>();
	
	private int windowSize;
	
	public DcHostStatusPolicy(int windowSize) {
		addRequiredCapability(DataCentreManager.class);
		
		this.windowSize = windowSize;
	}

	public void execute(HostStatusEvent event) {		
		HostPoolManager hostPool = manager.getCapability(DataCentreManager.class);
		
		hostPool.getHost(event.getHostStatus().getId()).addHostStatus(event.getHostStatus(), windowSize);
	}

	@Override
	public void onInstall() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStart() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStop() {
		// TODO Auto-generated method stub
		
	}

}
