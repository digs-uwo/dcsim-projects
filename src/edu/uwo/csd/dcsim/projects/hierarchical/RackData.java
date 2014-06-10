package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.common.HashCodeUtil;
import edu.uwo.csd.dcsim.host.*;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;

public class RackData {

	private Rack rack;
	private AutonomicManager rackManager;
	private RackDescription rackDescription;
	
	private RackStatus currentStatus = null;
//	private RackStatus sandboxStatus = null; //this is a RackStatus variable that can be freely modified for use in policies
	private boolean statusValid = true;
	private long invalidationTime = -1;
	
	private boolean active = false;
	
	private ArrayList<RackStatus> history = new ArrayList<RackStatus>();
	
	private final int hashCode;
	
	public RackData(Rack rack, AutonomicManager rackManager) {
		this.rack = rack;
		this.rackManager = rackManager;
		
		rackDescription = new RackDescription(rack);
		
		// Initialize current status with an *empty* record.
		currentStatus = new RackStatus(rack, 0);
		
		//init hashCode
		hashCode = generateHashCode();
	}
	
	public void addRackStatus(RackStatus rackStatus, int historyWindowSize) {
		currentStatus = rackStatus;
		
		if (rackStatus.getState() == Rack.RackState.ON)
			active = true;
		else
			active = false;
		
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
	
	/**
	 * Verifies whether the given Rack can meet the resource requirements of the VM, 
	 * considering the Rack's max spare capacity and number of suspended and powered off Hosts.
	 */
	public static boolean canHost(VmStatus vm, RackStatusVector currentStatus, RackDescription rackDescription) {
		if (RackData.calculateMinHostActivations(vm, currentStatus, rackDescription.getHostDescription()) >= 0)
			return true;
		
		return false;
	}
	
	public static boolean canHost(ConstrainedAppAllocationRequest request, RackStatusVector currentStatus, RackDescription rackDescription) {
		if (RackData.calculateMinHostActivations(request, currentStatus, rackDescription.getHostDescription()) >= 0)
			return true;
		
		return false;
	}
	
	public static boolean canHost(AppStatus application, RackStatusVector currentStatus, RackDescription rackDescription) {
		if (RackData.calculateMinHostActivations(application, currentStatus, rackDescription.getHostDescription()) >= 0)
			return true;
		
		return false;
	}
	
	/**
	 * Verifies whether the given Rack can meet the resource requirements of the application,
	 * based on the Rack's status vector.
	 */
	public static int calculateMinHostActivations(ConstrainedAppAllocationRequest request, RackStatusVector currentStatus, HostDescription hostDescription) {
		int failed = -1;
		
		// TODO: THIS METHOD DOES NOT CHECK THE HW CAPABILITIES OF THE RACK; THAT SHOULD BE DONE AT A HIGHER LEVEL.
		// AT THIS STAGE, WE ASSUME THAT ANY REQUEST THAT COMES THIS WAY WOULD HAVE ITS HW NEEDS MET (I.E., CPU CORES & CORE CAPACITY).
		
		// TODO: REFACTOR THIS METHOD.
		
		// Get Rack status vector.
		RackStatusVector statusVector = currentStatus.copy();
		
		// Affinity sets
		for (ArrayList<VmAllocationRequest> affinitySet : request.getAffinityVms()) {
			
			// Calculate total resource needs of the VMs in the affinity set.
			int totalCpu = 0;
			int totalMemory = 0;
			int totalBandwidth = 0;
			int totalStorage = 0;
			for (VmAllocationRequest req : affinitySet) {
				totalCpu += req.getCpu();
				totalMemory += req.getMemory();
				totalBandwidth += req.getBandwidth();
				totalStorage += req.getStorage();
			}
			Resources totalReqResources = new Resources(totalCpu, totalMemory, totalBandwidth, totalStorage);
			
			// Check if a currently active Host has enough spare capacity to host the set.
			boolean found = false;
			for (int i = statusVector.vmVector.length - 1; i >= 0; i--) {
				if (RackData.theresEnoughCapacity(totalReqResources, statusVector.vmVector[i])) {
					if (statusVector.vector[statusVector.iVmVector + i] > 0) {
						found = true;
						statusVector.vector[statusVector.iVmVector + i]--;
						Resources reminder = statusVector.vmVector[i].subtract(totalReqResources);
						RackData.updateSpareCapacityVector(statusVector, reminder, 1);
						break;
					}
				}
				else
					break;
			}
			
			if (!found) {	// Activate a new Host.
				if (statusVector.vector[statusVector.iSuspended] > 0 || statusVector.vector[statusVector.iPoweredOff] > 0) {
					Resources hostCapacity = hostDescription.getResourceCapacity();
					if (RackData.theresEnoughCapacity(totalReqResources, hostCapacity)) {
						found = true;
						if (statusVector.vector[statusVector.iSuspended] > 0)
							statusVector.vector[statusVector.iSuspended]--;
						else
							statusVector.vector[statusVector.iPoweredOff]--;
						statusVector.vector[statusVector.iActive]++;
						Resources reminder = hostCapacity.subtract(totalReqResources);
						RackData.updateSpareCapacityVector(statusVector, reminder, 1);
					}
					else
						return failed;
				}
				else
					return failed;
			}
		}
		
		// Anti-affinity sets
		for (ArrayList<VmAllocationRequest> antiAffinitySet : request.getAntiAffinityVms()) {
			
			// Note: All VMs in the set have equal size and MUST be placed in different Hosts each.
			
			if (antiAffinitySet.size() == 0)	// Checking that the set is not empty -- which should never occur, but...
				continue;
			Resources vmSize = antiAffinitySet.get(0).getResources();
			int nVms = antiAffinitySet.size();
			
			// for each VM, see if there's a Host that can take it; modify vector accordingly
			for (int i = 0; i < statusVector.vmVector.length; i++) {
				
				if (RackData.theresEnoughCapacity(vmSize, statusVector.vmVector[i])) {
					int hosts = Math.min(nVms, statusVector.vector[statusVector.iVmVector + i]);
					Resources reminder = statusVector.vmVector[i].subtract(vmSize);
					RackData.updateSpareCapacityVector(statusVector, reminder, hosts);
					nVms -= hosts;
					statusVector.vector[statusVector.iVmVector + i] -= hosts;
				}
				if (nVms == 0)	// All VMs were accounted for.
					break;
			}
			
			// If there still are VMs to account for, active suspended Hosts.
			if (nVms > 0 && statusVector.vector[statusVector.iSuspended] > 0) {
				int hosts = Math.min(nVms, statusVector.vector[statusVector.iSuspended]);
				Resources hostCapacity = hostDescription.getResourceCapacity();
				Resources reminder = hostCapacity.subtract(vmSize);
				RackData.updateSpareCapacityVector(statusVector, reminder, hosts);
				nVms -= hosts;
				statusVector.vector[statusVector.iSuspended] -= hosts;
				statusVector.vector[statusVector.iActive] += hosts;
			}
			
			// If there still are VMs to account for, active powered-off Hosts.
			if (nVms > 0 && statusVector.vector[statusVector.iPoweredOff] > 0) {
				int hosts = Math.min(nVms, statusVector.vector[statusVector.iPoweredOff]);
				Resources hostCapacity = hostDescription.getResourceCapacity();
				Resources reminder = hostCapacity.subtract(vmSize);
				RackData.updateSpareCapacityVector(statusVector, reminder, hosts);
				nVms -= hosts;
				statusVector.vector[statusVector.iPoweredOff] -= hosts;
				statusVector.vector[statusVector.iActive] += hosts;
			}
			
			if (nVms > 0)
				return failed;
		}
		
		// Independent set
		for (VmAllocationRequest req : request.getIndependentVms()) {
			// for each VM, see if there's a Host that can take it; modify vector accordingly
			boolean found = false;
			for (int i = 0; i < statusVector.vmVector.length; i++) {
				if (RackData.theresEnoughCapacity(req.getResources(), statusVector.vmVector[i])) {
					found = true;
					statusVector.vector[statusVector.iVmVector + i]--;
					Resources reminder = statusVector.vmVector[i].subtract(req.getResources());
					RackData.updateSpareCapacityVector(statusVector, reminder, 1);
					break;
				}
			}
			
			if (!found) {	// Activate a new Host.
				if (statusVector.vector[statusVector.iSuspended] > 0 || statusVector.vector[statusVector.iPoweredOff] > 0) {
					Resources hostCapacity = hostDescription.getResourceCapacity();
					if (RackData.theresEnoughCapacity(req.getResources(), hostCapacity)) {
						if (statusVector.vector[statusVector.iSuspended] > 0)
							statusVector.vector[statusVector.iSuspended]--;
						else
							statusVector.vector[statusVector.iPoweredOff]--;
						statusVector.vector[statusVector.iActive]++;
						Resources reminder = hostCapacity.subtract(req.getResources());
						RackData.updateSpareCapacityVector(statusVector, reminder, 1);
					}
					else
						return failed;
				}
				else
					return failed;
			}
		}
		
		return statusVector.vector[statusVector.iActive] - currentStatus.vector[currentStatus.iActive];
	}
	
	/**
	 * Verifies whether the given Rack can meet the resource requirements of the application,
	 * based on the Rack's status vector.
	 */
	public static int calculateMinHostActivations(AppStatus application, RackStatusVector currentStatus, HostDescription hostDescription) {
		int failed = -1;
		
		// TODO: THIS METHOD DOES NOT CHECK THE HW CAPABILITIES OF THE RACK; THAT SHOULD BE DONE AT A HIGHER LEVEL.
		// AT THIS STAGE, WE ASSUME THAT ANY REQUEST THAT COMES THIS WAY WOULD HAVE ITS HW NEEDS MET (I.E., CPU CORES & CORE CAPACITY).
		
		// TODO: REFACTOR THIS METHOD.
		
		// Get Rack status vector.
		RackStatusVector statusVector = currentStatus.copy();
		
		// Affinity sets
		for (ArrayList<VmStatus> affinitySet : application.getAffinityVms()) {
			
			// Calculate total resource needs of the VMs in the affinity set.
			Resources totalReqResources = new Resources();
			for (VmStatus vm : affinitySet) {
				totalReqResources = totalReqResources.add(vm.getResourcesInUse());
			}
			
			// Check if a currently active Host has enough spare capacity to host the set.
			boolean found = false;
			for (int i = statusVector.vmVector.length - 1; i >= 0; i--) {
				if (RackData.theresEnoughCapacity(totalReqResources, statusVector.vmVector[i])) {
					if (statusVector.vector[statusVector.iVmVector + i] > 0) {
						found = true;
						statusVector.vector[statusVector.iVmVector + i]--;
						Resources reminder = statusVector.vmVector[i].subtract(totalReqResources);
						RackData.updateSpareCapacityVector(statusVector, reminder, 1);
						break;
					}
				}
				else
					break;
			}
			
			if (!found) {	// Activate a new Host.
				if (statusVector.vector[statusVector.iSuspended] > 0 || statusVector.vector[statusVector.iPoweredOff] > 0) {
					Resources hostCapacity = hostDescription.getResourceCapacity();
					if (RackData.theresEnoughCapacity(totalReqResources, hostCapacity)) {
						found = true;
						if (statusVector.vector[statusVector.iSuspended] > 0)
							statusVector.vector[statusVector.iSuspended]--;
						else
							statusVector.vector[statusVector.iPoweredOff]--;
						statusVector.vector[statusVector.iActive]++;
						Resources reminder = hostCapacity.subtract(totalReqResources);
						RackData.updateSpareCapacityVector(statusVector, reminder, 1);
					}
					else
						return failed;
				}
				else
					return failed;
			}
		}
		
		// Anti-affinity sets
		for (ArrayList<VmStatus> antiAffinitySet : application.getAntiAffinityVms()) {
			
			// Note: All VMs in the set have equal size and MUST be placed in different Hosts each.
			
			// TODO: Is is true that all VMs in the set can be considered equal in terms of resource usage? Only if they are perfectly load balanced...
			
			if (antiAffinitySet.size() == 0)	// Checking that the set is not empty -- which should never occur, but...
				continue;
			Resources vmSize = antiAffinitySet.get(0).getResourcesInUse();
			int nVms = antiAffinitySet.size();
			
			// for each VM, see if there's a Host that can take it; modify vector accordingly
			for (int i = 0; i < statusVector.vmVector.length; i++) {
				
				if (RackData.theresEnoughCapacity(vmSize, statusVector.vmVector[i])) {
					int hosts = Math.min(nVms, statusVector.vector[statusVector.iVmVector + i]);
					Resources reminder = statusVector.vmVector[i].subtract(vmSize);
					RackData.updateSpareCapacityVector(statusVector, reminder, hosts);
					nVms -= hosts;
					statusVector.vector[statusVector.iVmVector + i] -= hosts;
				}
				if (nVms == 0)	// All VMs were accounted for.
					break;
			}
			
			// If there still are VMs to account for, active suspended Hosts.
			if (nVms > 0 && statusVector.vector[statusVector.iSuspended] > 0) {
				int hosts = Math.min(nVms, statusVector.vector[statusVector.iSuspended]);
				Resources hostCapacity = hostDescription.getResourceCapacity();
				Resources reminder = hostCapacity.subtract(vmSize);
				RackData.updateSpareCapacityVector(statusVector, reminder, hosts);
				nVms -= hosts;
				statusVector.vector[statusVector.iSuspended] -= hosts;
				statusVector.vector[statusVector.iActive] += hosts;
			}
			
			// If there still are VMs to account for, active powered-off Hosts.
			if (nVms > 0 && statusVector.vector[statusVector.iPoweredOff] > 0) {
				int hosts = Math.min(nVms, statusVector.vector[statusVector.iPoweredOff]);
				Resources hostCapacity = hostDescription.getResourceCapacity();
				Resources reminder = hostCapacity.subtract(vmSize);
				RackData.updateSpareCapacityVector(statusVector, reminder, hosts);
				nVms -= hosts;
				statusVector.vector[statusVector.iPoweredOff] -= hosts;
				statusVector.vector[statusVector.iActive] += hosts;
			}
			
			if (nVms > 0)
				return failed;
		}
		
		// Independent set
		for (VmStatus vm : application.getIndependentVms()) {
			// for each VM, see if there's a Host that can take it; modify vector accordingly
			boolean found = false;
			for (int i = 0; i < statusVector.vmVector.length; i++) {
				if (RackData.theresEnoughCapacity(vm.getResourcesInUse(), statusVector.vmVector[i])) {
					found = true;
					statusVector.vector[statusVector.iVmVector + i]--;
					Resources reminder = statusVector.vmVector[i].subtract(vm.getResourcesInUse());
					RackData.updateSpareCapacityVector(statusVector, reminder, 1);
					break;
				}
			}
			
			if (!found) {	// Activate a new Host.
				if (statusVector.vector[statusVector.iSuspended] > 0 || statusVector.vector[statusVector.iPoweredOff] > 0) {
					Resources hostCapacity = hostDescription.getResourceCapacity();
					if (RackData.theresEnoughCapacity(vm.getResourcesInUse(), hostCapacity)) {
						if (statusVector.vector[statusVector.iSuspended] > 0)
							statusVector.vector[statusVector.iSuspended]--;
						else
							statusVector.vector[statusVector.iPoweredOff]--;
						statusVector.vector[statusVector.iActive]++;
						Resources reminder = hostCapacity.subtract(vm.getResourcesInUse());
						RackData.updateSpareCapacityVector(statusVector, reminder, 1);
					}
					else
						return failed;
				}
				else
					return failed;
			}
		}
		
		return statusVector.vector[statusVector.iActive] - currentStatus.vector[currentStatus.iActive];
	}
	
	public static int calculateMinHostActivations(VmStatus vm, RackStatusVector currentStatus, HostDescription hostDescription) {
		int failed = -1;
		
		// TODO: THIS METHOD DOES NOT CHECK THE HW CAPABILITIES OF THE RACK; THAT SHOULD BE DONE AT A HIGHER LEVEL.
		// AT THIS STAGE, WE ASSUME THAT ANY REQUEST THAT COMES THIS WAY WOULD HAVE ITS HW NEEDS MET (I.E., CPU CORES & CORE CAPACITY).
		
		// TODO: REFACTOR THIS METHOD.
		
		// Get Rack status vector.
		RackStatusVector statusVector = currentStatus.copy();
		
		// Check whether an active Host has enough spare resources to fit the VM.
		for (int i = 0; i < statusVector.vmVector.length; i++) {
			
			// TODO: Shouldn't we be checking in the IF STATEMENT whether (statusVector.vector[statusVector.iVmVector + i] > 0) ??
			// (i.e., whether there is actually a Host with that capacity available) 
			
			if (RackData.theresEnoughCapacity(vm.getResourcesInUse(), statusVector.vmVector[i])) {
				return 0;
			}
		}
		
		// If no active Host could fit the VM, activate a new Host.
		if (statusVector.vector[statusVector.iSuspended] > 0 || statusVector.vector[statusVector.iPoweredOff] > 0) {
			Resources hostCapacity = hostDescription.getResourceCapacity();
			if (RackData.theresEnoughCapacity(vm.getResourcesInUse(), hostCapacity)) {
				return 1;
			}
			else
				return failed;
		}
		else
			return failed;
	}
	
	
	// TODO: Shouldn't this method belong in RackStatusVector class ??
	
	
	private static void updateSpareCapacityVector(RackStatusVector statusVector, Resources reminder, int count) {
		
		for (int i = statusVector.vmVector.length - 1; i >= 0; i--) {
			if (theresEnoughCapacity(statusVector.vmVector[i], reminder)) {
				statusVector.vector[statusVector.iVmVector + i] += count;
				break;
			}
		}
	}
	
	private static boolean theresEnoughCapacity(Resources requiredResources, Resources availableResources) {
		// Check available resources.
		if (availableResources.getCpu() < requiredResources.getCpu())
			return false;
		if (availableResources.getMemory() < requiredResources.getMemory())
			return false;
		if (availableResources.getBandwidth() < requiredResources.getBandwidth())
			return false;
		if (availableResources.getStorage() < requiredResources.getStorage())
			return false;
		
		return true;
	}
	
	public boolean isStatusValid() {
		return statusValid;
	}
	
	public void invalidateStatus(long time) {
		statusValid = false;
		invalidationTime = time;
	}
	
	public boolean isRackActive() {
		return active;
	}
	
	public void activateRack() {
		active = true;
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
	
	public RackStatus getCurrentStatus() {
		// Return a copy of the status to ensure that it is read-only.
		return currentStatus.copy();
	}
	
	public ArrayList<RackStatus> getHistory() {
		// Return a copy of the history to ensure that it is read-only.
		ArrayList<RackStatus> historyCopy = new ArrayList<RackStatus>();
		for (RackStatus status : history) {
			historyCopy.add(status.copy());
		}
		return historyCopy;
	}
	
	@Override
	public int hashCode() {
		return hashCode;
	}
	
	private int generateHashCode() {
		int result = HashCodeUtil.SEED;
		result = HashCodeUtil.hash(result, rack.getId());
		return result;
	}

}
