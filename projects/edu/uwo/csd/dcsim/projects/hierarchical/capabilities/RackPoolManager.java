package edu.uwo.csd.dcsim.projects.hierarchical.capabilities;

import java.util.*;

import edu.uwo.csd.dcsim.host.Rack;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.capabilities.ManagerCapability;
import edu.uwo.csd.dcsim.projects.hierarchical.RackData;

public class RackPoolManager extends ManagerCapability {
	
	private Map<Integer, RackData> rackMap = new HashMap<Integer, RackData>();
	
	public void addRack(Rack rack, AutonomicManager rackManager) {
		rackMap.put(rack.getId(), new RackData(rack, rackManager));
	}
	
	public Collection<RackData> getRacks() {
		return rackMap.values();
	}
	
	public RackData getRack(int id) {
		return rackMap.get(id);
	}

}
