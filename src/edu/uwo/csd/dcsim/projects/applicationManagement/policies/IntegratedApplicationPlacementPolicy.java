package edu.uwo.csd.dcsim.projects.applicationManagement.policies;

import java.util.*;

import edu.uwo.csd.dcsim.application.*;
import edu.uwo.csd.dcsim.common.Tuple;
import edu.uwo.csd.dcsim.common.Utility;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Rack;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.host.Host.HostState;
import edu.uwo.csd.dcsim.management.HostData;
import edu.uwo.csd.dcsim.management.HostDataComparator;
import edu.uwo.csd.dcsim.management.HostStatus;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.management.action.InstantiateVmAction;
import edu.uwo.csd.dcsim.management.events.ApplicationPlacementEvent;
import edu.uwo.csd.dcsim.projects.applicationManagement.RackComparator;
import edu.uwo.csd.dcsim.projects.applicationManagement.capabilities.*;
import edu.uwo.csd.dcsim.projects.applicationManagement.events.*;
import edu.uwo.csd.dcsim.vm.VmAllocationRequest;

public class IntegratedApplicationPlacementPolicy extends Policy {

	protected double lowerThreshold;
	protected double upperThreshold;
	protected double targetUtilization;
	
	private int rackRotation = 0; //used to rotate rack placement for applications placed at the same time
	private boolean topologyAware;
	private double rackTarget;
	
	public IntegratedApplicationPlacementPolicy(double lowerThreshold, double upperThreshold, double targetUtilization, boolean topologyAware, double rackTarget) {
		addRequiredCapability(DataCentreManager.class);
		
		this.lowerThreshold = lowerThreshold;
		this.upperThreshold = upperThreshold;
		this.targetUtilization = targetUtilization;
		this.topologyAware = topologyAware;
		this.rackTarget = rackTarget;
	}
	
	public void execute(ApplicationPlacementEvent event) {
			
		DataCentreManager dcManager = manager.getCapability(DataCentreManager.class);
		Collection<HostData> hosts = dcManager.getHosts();
		
		ArrayList<Application> applications = event.getApplications();
		
		ArrayList<InstantiateVmAction> actions = new ArrayList<InstantiateVmAction>();
		
		//reset the sandbox host status to the current host status
		for (HostData host : hosts) {
			host.resetSandboxStatusToCurrent();
		}
		
		//place each application individually
		for (Application application : applications) {
			ArrayList<InstantiateVmAction> appActions = placeApplication(application, dcManager, event); 
			if (appActions != null) {
				actions.addAll(appActions);
			} else {
				System.out.println("Placement failed for application #" + application.getId());
				event.setFailed(true);
			}
		}
		
		//execute actions
		for (InstantiateVmAction action : actions) {
			action.getTarget().invalidateStatus(simulation.getSimulationTime());
			action.execute(simulation, this);
		}
		
	}
	
	private ArrayList<InstantiateVmAction> placeApplication(Application application, DataCentreManager dcManager, ApplicationPlacementEvent event) {
		
		ArrayList<InstantiateVmAction> actions = new ArrayList<InstantiateVmAction>();
		
		ArrayList<ArrayList<VmAllocationRequest>> taskAllocationRequests = new ArrayList<ArrayList<VmAllocationRequest>>();
		for (Task task : application.getTasks()) {
			taskAllocationRequests.add(task.createInitialVmRequests());
		}
		
		//select Rack for deployment
		ApplicationPlacement placement = null;
		
		//attempt to place in the fewest number of racks possible
		ArrayList<Rack> targetRacks = new ArrayList<Rack>();
		targetRacks.addAll(dcManager.getRacks()); //create an ordered version of the rack collection
		
		//rotate initial collection to ensure that applications placed at the same time are spread despite "sort by powered on hosts" returning same order 
		Collections.rotate(targetRacks, rackRotation++);
		
		ArrayList<Rack> overTarget = new ArrayList<Rack>();
		ArrayList<Rack> underTarget = new ArrayList<Rack>();
		
		for (Rack rack : targetRacks) {
			int nHostsOn = 0;
			for (Host host : rack.getHosts()) {
				if (host.getState() == HostState.ON) ++nHostsOn;
			}
			if (nHostsOn / (double)rack.getHostCount() >= rackTarget) {
				overTarget.add(rack);
			} else {
				underTarget.add(rack);
			}
		}
		
		Collections.sort(underTarget, RackComparator.HOSTS_ON);
		Collections.reverse(underTarget);
		
		Collections.sort(overTarget, RackComparator.HOSTS_ON);
		
		targetRacks.clear();
		targetRacks.addAll(underTarget);
		targetRacks.addAll(overTarget);
		
		//sort targetRacks collection by decreasing number of powered on hosts
//		Collections.sort(targetRacks, RackComparator.HOSTS_ON);
//		Collections.reverse(targetRacks);
		
		if (topologyAware) {
		
			for (int nRacks = 1; nRacks <= targetRacks.size(); ++nRacks) {
	
				for (int i = 0; i <= targetRacks.size() - nRacks; ++i) {
					//build host set to attempt placement
					ArrayList<HostData> targetHosts = new ArrayList<HostData>();
					for (int j = i; j < i + nRacks; ++j) {
						targetHosts.addAll(dcManager.getHosts(targetRacks.get(j))); //get the HostData collection of the hosts belonging to this rack
					}
					
					placement = calculatePlacement(taskAllocationRequests, filterHosts(targetHosts), dcManager);
					
					if (placement.success) {
						//debugging output
	//					String out = "Placing application #" + application.getId() + " in rack(s)";
	//					for (int j = i; j < i + nRacks; ++j) {
	//						out = out + " - " + targetRacks.get(j).getId();
	//					}
	//					System.out.println(out);
						
						break;
					}
				}
				
				if (placement != null && placement.success) break;
			}
		
		} else {
			placement = calculatePlacement(taskAllocationRequests, filterHosts(dcManager.getHosts()), dcManager);
		}
		
		//build placement actions
		if (placement != null && placement.success) {
			for (Tuple<VmAllocationRequest, HostData> vmPlacement : placement.placements) {
				actions.add(new InstantiateVmAction(vmPlacement.b, vmPlacement.a, event));
				
				vmPlacement.b.getSandboxStatus().instantiateVm(
						new VmStatus(vmPlacement.a.getVMDescription().getCores(),
								vmPlacement.a.getVMDescription().getCoreCapacity(),
						vmPlacement.a.getResources()));
				
				vmPlacement.b.invalidateStatus(simulation.getSimulationTime());
			}
			
			return actions;
		}
		
		return null;		
	}
	
	private ApplicationPlacement calculatePlacement(ArrayList<ArrayList<VmAllocationRequest>> taskAllocationRequests, Collection<HostData> hosts, DataCentreManager dcManager) {
		
		ApplicationPlacement placement = new ApplicationPlacement();
		
		Map<HostData, HostStatus> hostStatusMap = new HashMap<HostData, HostStatus>(); 
		
		//build a copy of all host status info to use for our temporary placement calculations. We don't want to modify the host sandbox status,
		//as it may be already modified by previous action and these placements may not be executed
		for (HostData host : hosts) {
			hostStatusMap.put(host, host.getSandboxStatus().copy());
		}
		
		ArrayList<HostData> partiallyUtilized = new ArrayList<HostData>();
		ArrayList<HostData> underUtilized = new ArrayList<HostData>();
		ArrayList<HostData> empty = new ArrayList<HostData>();
		
		this.classifyHosts(partiallyUtilized, underUtilized, empty, hosts);
		
		// Create target hosts list.
		ArrayList<HostData> targets = this.orderTargetHosts(partiallyUtilized, underUtilized, empty);
		
		//for now, just place all task instances in order. Later, change to spread out instances of the same task
		ArrayList<VmAllocationRequest> requests = new ArrayList<VmAllocationRequest>();
		for (ArrayList<VmAllocationRequest> taskRequests : taskAllocationRequests) {
			requests.addAll(taskRequests);
		}
		
		for (VmAllocationRequest request : requests) {

			HostData allocatedHost = null;
			for (HostData target : targets) {
				Resources reqResources = new Resources();
				reqResources.setCpu(request.getCpu());
				reqResources.setMemory(request.getMemory());
				reqResources.setBandwidth(request.getBandwidth());
				reqResources.setStorage(request.getStorage());

				if (HostData.canHost(request.getVMDescription().getCores(), 	//target has capability and capacity to host VM 
						request.getVMDescription().getCoreCapacity(), 
						reqResources,
						hostStatusMap.get(target),
						target.getHostDescription()) &&
						(hostStatusMap.get(target).getResourcesInUse().getCpu() + request.getCpu()) / target.getHostDescription().getResourceCapacity().getCpu() <= targetUtilization)
						{
					
					allocatedHost = target;
					
					//add a dummy placeholder VM to keep track of placed VM resource requirements
					hostStatusMap.get(target).instantiateVm(
							new VmStatus(request.getVMDescription().getCores(),
									request.getVMDescription().getCoreCapacity(),
							reqResources));
					
					placement.placements.add(new Tuple<VmAllocationRequest, HostData>(request, target));
					
					break;
				 }
			}
			
			if (allocatedHost == null) {
				placement.success = false;
			}
		}
		
		return placement;
	}
	
	private class ApplicationPlacement {
		boolean success = true;
		ArrayList<Tuple<VmAllocationRequest, HostData>> placements = new ArrayList<Tuple<VmAllocationRequest, HostData>>();
	}
	
	public void execute(TaskInstancePlacementEvent event) {
		//throw an exception - for this work, we are handling new instance placement in the application management policy
		throw new RuntimeException("Placement Policy recieved TaskInstancePlacementEvent - We aren't currently handling this here!!");
	}
	
	public void execute(ShutdownApplicationEvent event) {
		//TODO: handle
		System.out.println("!!shutdown app event!!");
	}
	
	private Collection<HostData> filterHosts(Collection<HostData> hosts) {
		ArrayList<HostData> filteredHosts = new ArrayList<HostData>();
		
		for (HostData host : hosts) {
			if (host.isStatusValid()) {
				filteredHosts.add(host);
			}
		}
		
		return filteredHosts;
	}
	
	/**
	 * Classifies hosts as Partially-Utilized, Underutilized or Empty based on 
	 * the hosts' average CPU utilization over the last window of time.
	 */
	protected void classifyHosts(ArrayList<HostData> partiallyUtilized, 
			ArrayList<HostData> underUtilized, 
			ArrayList<HostData> empty, 
			Collection<HostData> hosts) {
		
		for (HostData host : hosts) {
			// Calculate host's avg CPU utilization over the last window of time.
			double avgCpuInUse = 0;
			int count = 0;
			for (HostStatus status : host.getHistory()) {
				// Only consider times when the host is powered on.
				if (status.getState() == Host.HostState.ON) {
					avgCpuInUse += status.getResourcesInUse().getCpu();
					++count;
				}
				else
					break;
			}
			if (count != 0) {
				avgCpuInUse = avgCpuInUse / count;
			}
			
			double avgCpuUtilization = Utility.roundDouble(avgCpuInUse / host.getHostDescription().getResourceCapacity().getCpu());
			
			// Classify hosts.
			if (host.getCurrentStatus().getVms().size() == 0) {
				empty.add(host);
			} else if (avgCpuUtilization < lowerThreshold) {
				underUtilized.add(host);
			} else if (avgCpuUtilization <= upperThreshold) {
				partiallyUtilized.add(host);
			}
		}
	}
	
	protected ArrayList<HostData> orderTargetHosts(ArrayList<HostData> partiallyUtilized, ArrayList<HostData> underUtilized, ArrayList<HostData> empty) {
		ArrayList<HostData> targets = new ArrayList<HostData>();
		
		// Sort Partially-utilized in increasing order by <CPU utilization, power efficiency>.
		Collections.sort(partiallyUtilized, HostDataComparator.getComparator(HostDataComparator.CPU_UTIL, HostDataComparator.EFFICIENCY));
		
		// Sort Underutilized hosts in decreasing order by <CPU utilization, power efficiency>.
		Collections.sort(underUtilized, HostDataComparator.getComparator(HostDataComparator.CPU_UTIL, HostDataComparator.EFFICIENCY));
		Collections.reverse(underUtilized);
		
		// Sort Empty hosts in decreasing order by <power efficiency, power state>.
		Collections.sort(empty, HostDataComparator.getComparator(HostDataComparator.EFFICIENCY, HostDataComparator.PWR_STATE));
		Collections.reverse(empty);
		
		targets.addAll(partiallyUtilized);
		targets.addAll(underUtilized);
		targets.addAll(empty);
		
		return targets;
	}
	
	@Override
	public void onInstall() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStart() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onManagerStop() {
		// TODO Auto-generated method stub
		
	}

}
