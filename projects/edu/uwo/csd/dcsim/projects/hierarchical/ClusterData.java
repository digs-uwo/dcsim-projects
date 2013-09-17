package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.management.*;

public class ClusterData {

	private Cluster cluster;
	private AutonomicManager clusterManager;
	private ClusterDescription clusterDescription;
	
	private ClusterStatus currentStatus = null;
//	private ClusterStatus sandboxStatus = null; //this is a ClusterStatus variable that can be freely modified for use in policies
	private boolean statusValid = true;
	private long invalidationTime = -1;
	
	private boolean active = false;
	
	private ArrayList<ClusterStatus> history = new ArrayList<ClusterStatus>();
	
	public ClusterData(Cluster cluster, AutonomicManager clusterManager) {
		this.cluster = cluster;
		this.clusterManager = clusterManager;
		
		clusterDescription = new ClusterDescription(cluster);
		
		// Initialize current status.
		currentStatus = new ClusterStatus(cluster, 0);
	}
	
	public void addClusterStatus(ClusterStatus clusterStatus, int historyWindowSize) {
		currentStatus = clusterStatus;
		
		if (clusterStatus.getState() == Cluster.ClusterState.ON)
			active = true;
		else
			active = false;
		
//		if (sandboxStatus == null) {
//			resetSandboxStatusToCurrent();
//		}
		
		// Only return the status to 'valid' if the update was sent later than the time when the status was invalidated.
		// TODO this might cause problems if, instead of waiting for the next status, we request an immediate update
		// with the message arriving at the same sim time.
		if (clusterStatus.getTimeStamp() > invalidationTime) {
			statusValid = true; // If status had been invalidated, we now know it is correct.
		}
		
		history.add(0, clusterStatus);
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
	
	public boolean isClusterActive() {
		return active;
	}
	
	public void activateCluster() {
		active = true;
	}
	
	public int getId() {
		return cluster.getId();
	}
	
	public Cluster getCluster() {
		return cluster;
	}
	
	public AutonomicManager getClusterManager() {
		return clusterManager;
	}
	
	public ClusterDescription getClusterDescription() {
		return clusterDescription;
	}
	
	public ClusterStatus getCurrentStatus() {
		// Return a copy of the status to ensure that it is read-only.
		return currentStatus.copy();
	}
	
	public ArrayList<ClusterStatus> getHistory() {
		// Return a copy of the history to ensure that it is read-only.
		ArrayList<ClusterStatus> historyCopy = new ArrayList<ClusterStatus>();
		for (ClusterStatus status : history) {
			historyCopy.add(status.copy());
		}
		return historyCopy;
	}

}
