package edu.uwo.csd.dcsim.projects.hierarchical.capabilities;

import java.util.HashSet;

import edu.uwo.csd.dcsim.management.capabilities.ManagerCapability;

public class VmMigrationsManager extends ManagerCapability {

	private HashSet<Integer> migratingVms = new HashSet<Integer>();
	
	public void addMigratingVm(int vmId) {
		migratingVms.add(vmId);
	}
	
	public boolean removeMigratingVm(int vmId) {
		return migratingVms.remove(vmId);
	}
	
	public boolean isMigrating(int vmId) {
		return migratingVms.contains(vmId);
	}

}
