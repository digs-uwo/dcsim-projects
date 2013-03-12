package edu.uwo.csd.dcsim.projects.hierarchical;

import edu.uwo.csd.dcsim.host.Rack;
import edu.uwo.csd.dcsim.management.HostDescription;

public class RackDescription {

	private int hostCount;
	private HostDescription hostDescription;			// Static resource capacity of a single Host.
	
	// power efficiency ???
	
	public RackDescription(Rack rack) {
		hostCount = rack.getHostCount();
		hostDescription = new HostDescription(rack.getHosts().get(0));
	}
	
	public int getHostCount() {
		return hostCount;
	}
	
	public HostDescription getHostDescription() {
		return hostDescription;
	}

}
