package edu.uwo.csd.dcsim.projects.hierarchical;

import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.management.HostStatus;

public class RackStatus {

	private long timeStamp;
	private int id;
	
	private int activeHosts;
	private int suspendedHosts;
	private int poweredOffHosts;
	
	// max spare capacity: list or single value (vector, actually)
	
	private double powerConsumption;
	
	public RackStatus(Rack rack, long timeStamp) {
		// TODO Auto-generated constructor stub
	}

	public HostStatus copy() {
		// TODO Auto-generated method stub
		return null;
	}

	public long getTimeStamp() {
		// TODO Auto-generated method stub
		return 0;
	}

}
