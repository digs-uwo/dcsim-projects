package edu.uwo.csd.dcsim.projects.hierarchical;

import edu.uwo.csd.dcsim.host.Rack;
import edu.uwo.csd.dcsim.management.HostDescription;

public class RackDescription {

	private int hostCount;								// Number of Hosts in the Rack.
	private HostDescription hostDescription;			// Static resource capacity of a single Host.
	private double powerEfficiency = 0;				// Total CPU units over power consumption when the Rack is fully utilized.
	
	// TODO Power efficiency only takes into account power consumption of the Hosts; it does not include the power consumption 
	// of the Switches. For that purpose, we'd need HostDescription to provide the power consumption of the Host at full load, 
	// and we'd need RackDescription to include number of Switches in the Rack and their power consumption (or description).
	
	public RackDescription(Rack rack) {
		hostCount = rack.getHostCount();
		hostDescription = new HostDescription(rack.getHosts().get(0));
		powerEfficiency = hostDescription.getPowerEfficiency() * hostCount;
	}
	
	public int getHostCount() {
		return hostCount;
	}
	
	public HostDescription getHostDescription() {
		return hostDescription;
	}
	
	public double getPowerEfficiency() {
		return powerEfficiency;
	}

}
