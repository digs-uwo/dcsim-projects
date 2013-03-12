package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.management.*;

public class RackData {

	private Rack rack;
	private AutonomicManager rackManager;
	private RackDescription rackDescription;
	
	private RackStatus currentStatus = null;
//	private RackStatus sandboxStatus = null; //this is a RackStatus variable that can be freely modified for use in policies
	private boolean statusValid = true;
	private long invalidationTime = -1;
	
	private ArrayList<RackStatus> history = new ArrayList<RackStatus>();
	
	public RackData(Rack rack, AutonomicManager rackManager) {
		this.rack = rack;
		this.rackManager = rackManager;
		
		rackDescription = new RackDescription(rack);
		
		// Initialize currentStatus (in order to maintain a status of powered off Racks ??? ).
		currentStatus = new RackStatus(rack, 0);
	}
	
	public void addRackStatus(RackStatus rackStatus, int historyWindowSize) {
		currentStatus = rackStatus;
//		if (sandboxStatus == null) {
//			resetSandboxStatusToCurrent();
//		}
		
		// Only return the status to 'valid' if the update was sent later than the time when the status was invalidated.
		// TODO this might cause problems if, instead of waiting for the next status, we request an immediate update
		// with the message arriving at the same sim time.
		if (rackStatus.getTimeStamp() > invalidationTime) {
			statusValid = true; // If status had been invalidated, we now know it is correct.
		}
		
		history.add(0, rackStatus);
		if (history.size() > historyWindowSize) {
			history.remove(history.size() - 1);
		}
	}
	
	public boolean isStatusValid() {
		return statusValid;
	}
	
	public void invalidateStatus(long time) {
		statusValid = false;
		invalidationTime = time;
	}
	
	public int getId() {
		return rack.getId();
	}
	
	public Rack getRack() {
		return rack;
	}
	
	public AutonomicManager getRackManager() {
		return rackManager;
	}
	
	public RackDescription getRackDescription() {
		return rackDescription;
	}
	
	public HostStatus getCurrentStatus() {
		// Return a copy of the status to ensure that it is read-only.
		return currentStatus.copy();
	}
	
	public ArrayList<RackStatus> getHistory() {
		// Return a copy of the history to ensure that it is read-only.
		ArrayList<RackStatus> historyCopy = new ArrayList<RackStatus>();
		for (RackStatus status : history) {
			historyCopy.add(status);
		}
		return historyCopy;
	}

}
