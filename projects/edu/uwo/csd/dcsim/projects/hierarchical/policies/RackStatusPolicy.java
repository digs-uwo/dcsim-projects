package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.RackPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.events.RackStatusEvent;

public class RackStatusPolicy extends Policy {

	ArrayList<Class<? extends Event>> triggerEvents = new ArrayList<Class<? extends Event>>();
	
	private int windowSize;
	
	public RackStatusPolicy(int windowSize) {
		addRequiredCapability(RackPoolManager.class);
		
		this.windowSize = windowSize;
	}

	public void execute(RackStatusEvent event) {		
		RackPoolManager capability = manager.getCapability(RackPoolManager.class);
		
		capability.getRack(event.getRackStatus().getId()).addRackStatus(event.getRackStatus(), windowSize);
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
