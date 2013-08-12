package edu.uwo.csd.dcsim.projects.hierarchical.capabilities;

import java.util.ArrayList;
import java.util.Collection;

import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.capabilities.HostCapability;
import edu.uwo.csd.dcsim.projects.hierarchical.MigRequestEntry;

public class MigRequestRecord extends HostCapability {

	private Collection<MigRequestEntry> record = new ArrayList<MigRequestEntry>();
	
	public void addEntry(MigRequestEntry entry) {
		record.add(entry);
	}
	
	public boolean removeEntry(MigRequestEntry entry) {
		return record.remove(entry);
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
