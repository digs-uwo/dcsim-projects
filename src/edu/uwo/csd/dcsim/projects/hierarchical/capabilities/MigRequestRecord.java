package edu.uwo.csd.dcsim.projects.hierarchical.capabilities;

import java.util.ArrayList;
import java.util.Collection;

import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.capabilities.ManagerCapability;
import edu.uwo.csd.dcsim.projects.hierarchical.AppStatus;
import edu.uwo.csd.dcsim.projects.hierarchical.MigRequestEntry;

public class MigRequestRecord extends ManagerCapability {

	private Collection<MigRequestEntry> record = new ArrayList<MigRequestEntry>();
	
	public void addEntry(MigRequestEntry entry) {
		record.add(entry);
	}
	
	public boolean removeEntry(MigRequestEntry entry) {
		return record.remove(entry);
	}
	
	public MigRequestEntry getEntry(AppStatus application, AutonomicManager origin) {
		for (MigRequestEntry entry : record) {
			if (entry.getApplication() == application && entry.getOrigin() == origin) {
				return entry;
			}
		}
		
		return null;
	}
	
	public MigRequestEntry getEntry(VmStatus vm, AutonomicManager origin) {
		for (MigRequestEntry entry : record) {
			if (entry.getVm() == vm && entry.getOrigin() == origin) {
				return entry;
			}
		}
		
		return null;
	}
	
	public Collection<MigRequestEntry> getEntries() { return record; }

}
