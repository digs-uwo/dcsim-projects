package edu.uwo.csd.dcsim.projects.hierarchical.capabilities;

import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.management.capabilities.HostCapability;

public class RackManager extends HostCapability {

	private Rack rack;
	
	public RackManager(Rack rack) {
		this.rack = rack;
	}
	
	public Rack getRack() {
		return rack;
	}

}
