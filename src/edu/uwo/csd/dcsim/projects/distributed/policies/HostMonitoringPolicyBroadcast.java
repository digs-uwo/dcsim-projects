package edu.uwo.csd.dcsim.projects.distributed.policies;

import java.util.ArrayList;
import java.util.Collections;

import edu.uwo.csd.dcsim.common.SimTime;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.host.comparator.HostComparator;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.action.MigrationAction;
import edu.uwo.csd.dcsim.management.action.SequentialManagementActionExecutor;
import edu.uwo.csd.dcsim.management.action.ShutdownHostAction;
import edu.uwo.csd.dcsim.projects.distributed.actions.AcceptOfferAction;
import edu.uwo.csd.dcsim.projects.distributed.actions.RejectOfferAction;
import edu.uwo.csd.dcsim.projects.distributed.actions.UpdatePowerStateListAction;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.*;
import edu.uwo.csd.dcsim.projects.distributed.DistributedMetrics;
import edu.uwo.csd.dcsim.projects.distributed.DistributedTestEnvironment;
import edu.uwo.csd.dcsim.projects.distributed.Eviction;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.HostManagerBroadcast.ManagementState;
import edu.uwo.csd.dcsim.projects.distributed.capabilities.HostManagerBroadcast.ShutdownState;
import edu.uwo.csd.dcsim.projects.distributed.comparators.ResourceOfferComparator;
import edu.uwo.csd.dcsim.projects.distributed.events.*;
import edu.uwo.csd.dcsim.projects.distributed.events.RequestResourcesEvent.AdvertiseReason;

public class HostMonitoringPolicyBroadcast extends Policy {

	private static final int EVICTION_WAIT_TIME = 500; //the number of milliseconds to wait to evict a VM
	private static final int STRESS_MONITOR_WINDOW = 5;
	private static final int UNDER_MONITOR_WINDOW = 5;
	
	private static final long SHUTDOWN_SUBSEQUENT_FREEZE_DURATION = SimTime.minutes(5); //after another host has shut down
	private static final long SHUTDOWN_FREEZE_AFTER_BOOT_DURATION = SimTime.minutes(15); //after this host has started
	private static final long SHUTDOWN_FREEZE_AFTER_FAIL_DURATION = SimTime.minutes(15); //triggered after a failed shutdown
	private static final long EVICTION_FREEZE_DURATION = SimTime.minutes(30);
	private static final long OFFER_FREEZE_DURATION = SimTime.minutes(30);
	
	private static DistributedMetrics distributedMetrics = null;
	
	private double lower;
	private double upper;
	private double target;
	
	public HostMonitoringPolicyBroadcast(double lower, double upper, double target) {
		addRequiredCapability(HostManagerBroadcast.class);
		
		this.lower = lower;
		this.upper = upper;
		this.target = target;
	}
	
	/**
	 * Execute on a regular interval, handling the operation of the host
	 */
	public void execute() {
		
		if (distributedMetrics == null) {
			distributedMetrics = DistributedTestEnvironment.getDistributedMetrics(simulation);
		}
		
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		/*
		 * Monitoring
		 */
		HostStatus hostStatus = new HostStatus(hostManager.getHost(), simulation.getSimulationTime());
		hostManager.addHistoryStatus(hostStatus, Math.max(STRESS_MONITOR_WINDOW, UNDER_MONITOR_WINDOW));
		
		//if a migration is pending, wait until it is complete
		if ((hostStatus.getIncomingMigrationCount() > 0) || (hostStatus.getOutgoingMigrationCount() > 0)) return;
		
		
		/*
		 * NORMAL STATE (no evictions, not shutting down, not bidding)
		 * 
		 * Detect stress and underutilization
		 * 
		 */
		if (hostManager.getManagementState() == ManagementState.NORMAL && hostManager.getShutdownState() == ShutdownState.NONE) {

			/*
			 * STRESSED (detect stressed state)
			 * 
			 * Trigger a VM eviction
			 *  
			 */
			if (isStressed(hostManager) && !evictionFrozen(hostManager)) {
				//host is stressed, evict a VM

				//sort VMs
				ArrayList<VmStatus> vmList = orderVmsStressed(hostStatus.getVms(), hostManager.getHost());
				
				//evict first VM in list
				if (!vmList.isEmpty()) {
					if (simulation.isRecordingMetrics())
						distributedMetrics.stressEvict++;
					evict(hostManager, vmList, RequestResourcesEvent.AdvertiseReason.STRESS);	
					
					//freeze VM bidding
					hostManager.enactOfferFreeze(simulation.getSimulationTime() + OFFER_FREEZE_DURATION);
				}
				
			} 
			
			/*
			 * UNDERUTILIZED (detect underutilized state)
			 *
			 * Decide whether or not to switch into SHUTDOWN
			 */
			
			else if (isUnderUtilized(hostManager, hostStatus) && !shutdownFrozen(hostManager)) {
				
				//host is underutilized, attempt to shutdown
				if (!shutdownFrozen(hostManager) &&
						getActiveHostCount(hostManager) > 1) {
					
					//evict VMs
					if (!hostStatus.getVms().isEmpty()) {
						triggerShutdownElection(hostManager, hostStatus);
					} else {
						//indicate that resources are available, as we don't know, and therefore don't want to prevent another host from attempting shutdown
						hostManager.setShutdownResourcesAvailable(true);
						ShutdownHostAction action = new ShutdownHostAction(hostManager.getHost());
						action.execute(simulation, this);
					}
				}

			}
		}


	}
	
	/**
	 * Evict a single VM from this host
	 * 
	 * @param hostManager
	 * @param vm
	 */
	private void evict(HostManagerBroadcast hostManager, ArrayList<VmStatus> vmList, RequestResourcesEvent.AdvertiseReason reason) {
		//setup eviction counter/data
		Resources minResources = getMinResources(vmList);

		Eviction eviction = new Eviction();
		eviction.setVmList(vmList);
		
		hostManager.setEviction(eviction);
		hostManager.setManagementState(ManagementState.EVICTING);
		
		RequestResourcesEvent event = new RequestResourcesEvent(hostManager.getBroadcastingGroup(), minResources, eviction, manager, reason);
		
		eviction.setEvent(event);
		
		//send advertise message
		simulation.sendEvent(event);	
		simulation.sendEvent(new EvictionEvent(manager, eviction), simulation.getSimulationTime() + EVICTION_WAIT_TIME);
	}
	
	private void triggerShutdownElection(HostManagerBroadcast hostManager, HostStatus hostStatus) {
		//set up shutdown claims
		hostManager.getShutdownClaims().clear();
		
		//create a claim for this host
		ShutdownClaimEvent selfClaim = new ShutdownClaimEvent(manager, manager, hostManager.getHost(), hostStatus.getResourcesInUse(), hostStatus.getVms().size());
		hostManager.getShutdownClaims().add(selfClaim);
		hostManager.setShutdownState(ShutdownState.COORDINATING);
		
		//send a broadcast message to get shutdown claims
		simulation.sendEvent(new TriggerShutdownEvent(hostManager.getBroadcastingGroup(), manager, hostManager.getHost(), hostStatus.getResourcesInUse(), hostStatus.getVms().size()));
		
		//set an event to trigger completion of the election, after claims have been received
		simulation.sendEvent(new ShutdownElectionEvent(manager), simulation.getSimulationTime() + EVICTION_WAIT_TIME);
}
	
	public void execute(ShutdownElectionEvent event) {
		
		if (distributedMetrics == null) {
			distributedMetrics = DistributedTestEnvironment.getDistributedMetrics(simulation);
		}
		
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		//choose shutdown claim to accept (there will be at least one, from this host)
		//select the host with the lowest CPU utilization TODO consider memory as well
		ShutdownClaimEvent winner = null;
		for (ShutdownClaimEvent claim : hostManager.getShutdownClaims()) {
			if (winner == null) {
				winner = claim;
			} else {
				if (claim.getCandidateHost().getPowerEfficiency(1) < winner.getCandidateHost().getPowerEfficiency(1)) {
					winner = claim;
				} else {
					if (claim.getResourcesInUse().getCpu() < winner.getResourcesInUse().getCpu()) {
						winner = claim;
					}
				}
			}
		}
		
		simulation.sendEvent(new AwardShutdownEvent(winner.getCandidateAM()));
		for (ShutdownClaimEvent claim : hostManager.getShutdownClaims()) {
			simulation.sendEvent(new DenyShutdownEvent(claim.getCandidateAM()));
		}
		
		hostManager.getShutdownClaims().clear();
	}
	
	public void execute(ShutdownClaimEvent event) {
		
		if (distributedMetrics == null) {
			distributedMetrics = DistributedTestEnvironment.getDistributedMetrics(simulation);
		}
		
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		if (hostManager.getShutdownState() == ShutdownState.COORDINATING) {
			hostManager.getShutdownClaims().add(event);
		} else {
			simulation.sendEvent(new DenyShutdownEvent(event.getCandidateAM()));
		}
		
		if (simulation.isRecordingMetrics())
			distributedMetrics.msgBasic++; //only using CPU value
	}
	
	public void execute(TriggerShutdownEvent event) {
		
		if (distributedMetrics == null) {
			distributedMetrics = DistributedTestEnvironment.getDistributedMetrics(simulation);
		}
		
		//decide if we want to send a claim
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		HostStatus hostStatus = new HostStatus(hostManager.getHost(), simulation.getSimulationTime());
		
		if (simulation.isRecordingMetrics())
			distributedMetrics.msgBasic++; //only using CPU value
		
		//if a migration is pending, wait until it is complete
		if ((hostStatus.getIncomingMigrationCount() > 0) || (hostStatus.getOutgoingMigrationCount() > 0)) return;
		
		if (event.getCoordinator() != manager) {
			if (hostManager.getManagementState() == ManagementState.NORMAL && hostManager.getShutdownState() == ShutdownState.NONE) {
				if (isUnderUtilized(hostManager, hostStatus) && !shutdownFrozen(hostManager)) {
					Resources resources = hostStatus.getResourcesInUse();
					if (hostManager.getHost().getPowerEfficiency(1) < event.getCoordinatorHost().getPowerEfficiency(1) || resources.getCpu() < event.getResources().getCpu()) {
						
						//send claim to coordinator
						simulation.sendEvent(new ShutdownClaimEvent(event.getCoordinator(), manager, hostManager.getHost(), hostStatus.getResourcesInUse(), hostStatus.getVms().size()));
						
						//set state to prevent other actions while claiming shutdown
						hostManager.setShutdownState(ShutdownState.SUBMITTED_CLAIM);
						
					}
				}
			}
		}
	}
	
	public void execute(AwardShutdownEvent event) {
		
		if (distributedMetrics == null) {
			distributedMetrics = DistributedTestEnvironment.getDistributedMetrics(simulation);
		}
		
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		HostStatus hostStatus = new HostStatus(hostManager.getHost(), simulation.getSimulationTime());
		
		hostManager.setShutdownState(ShutdownState.SHUTTING_DOWN);
		
		ArrayList<VmStatus> vmList = orderVmsUnderUtilized(hostStatus.getVms());
		
		if (simulation.isRecordingMetrics())
			distributedMetrics.shutdownTriggered++;
	
		evict(hostManager, vmList, RequestResourcesEvent.AdvertiseReason.SHUTDOWN);	
		
		if (simulation.isRecordingMetrics())
			distributedMetrics.msgBasic++;
	}
	
	public void execute(DenyShutdownEvent event) {
		
		if (distributedMetrics == null) {
			distributedMetrics = DistributedTestEnvironment.getDistributedMetrics(simulation);
		}
		
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		hostManager.setShutdownState(ShutdownState.NONE);
		
		//must wait before attempting shutdown again
		hostManager.enactShutdownFreeze(simulation.getSimulationTime() + SHUTDOWN_SUBSEQUENT_FREEZE_DURATION);
		
		if (simulation.isRecordingMetrics())
			distributedMetrics.msgBasic++;
	}
	
	public void execute(ShutdownFailedEvent event) {
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		//freeze shutdown, since it's unlikely that another shutdown will succeed soon
		hostManager.enactShutdownFreeze(simulation.getSimulationTime() + SHUTDOWN_FREEZE_AFTER_FAIL_DURATION);
	}
	
	public void execute(EvictionEvent event) {
		
		if (distributedMetrics == null) {
			distributedMetrics = DistributedTestEnvironment.getDistributedMetrics(simulation);
		}
		
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		if (event.getEviction() != hostManager.getEviction())
			throw new RuntimeException("Eviction event does not match current eviction");
		
		if (event.getEviction().getEvent().getReason() == AdvertiseReason.STRESS) {
			completeStressEviction(event);
		} else {
			completeShutdownEviction(event);
		}
		
		if (simulation.isRecordingMetrics())
			distributedMetrics.msgBasic++;
	}
	
	private void completeStressEviction(EvictionEvent event) {
		
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		Eviction eviction = hostManager.getEviction();
		
		//filter out VMs from the VM list that have completed execution since the eviction notice. Note, VMs are already sorted
		ArrayList<VmStatus> vmList = new ArrayList<VmStatus>();
		for (VmStatus vm : eviction.getVmList()) {
			if (hostManager.getHost().getVMAllocation(vm.getId()) != null) {
				vmList.add(vm);
			}
		}
		
		boolean targetFound = false;
		
		//check for accepts
		if (eviction.getResourceOffers().size() > 0) {
			/*
			 * 
			 * Hosts have replied with spare capacity. Select a target for migration.
			 * 
			 */

			//Sort target hosts
			ArrayList<ResourceOfferEvent> targets = sortTargetHostsForStressEviction(eviction.getResourceOffers());
			
			//loop until a target is found
			ResourceOfferEvent target = null;
			VmStatus vmToMig = null;
			
			for (VmStatus vm : vmList) {
				for (ResourceOfferEvent hostOffer : targets) {
					Host host = hostOffer.getHost(); //to check capabilities. Can be assumed known by this host manager, since capabilities are static.
					HostStatus hostStatus = hostOffer.getHostStatus();
					Resources resourcesOffered = hostOffer.getResourcesOffered();
									
					//check capability & capacity
					if (canHost(host, hostStatus, vm) &&
							vm.getResourcesInUse().getCpu() <= resourcesOffered.getCpu() &&
							vm.getResourcesInUse().getMemory() <= resourcesOffered.getMemory() &&
							vm.getResourcesInUse().getBandwidth() <= resourcesOffered.getBandwidth() &&
							vm.getResourcesInUse().getStorage() <= resourcesOffered.getStorage()
							) {

						target = hostOffer;
						vmToMig = vm;
						break;
					}
				}
				
				if (target != null) {
					break;
				}
				
			}

			//trigger migration, send accept message
			if (target != null) {
				//trigger migration
				MigrationAction migAction = new MigrationAction(manager, hostManager.getHost(), target.getHost(), vmToMig.getId(), true);
				
				SequentialManagementActionExecutor actions = new SequentialManagementActionExecutor();	
				actions.addAction(migAction);
				
				//send offer accept messsage
				AcceptOfferAction acceptAction = new AcceptOfferAction(target.getHostManager(), target);
				actions.addAction(acceptAction);
				
				actions.execute(simulation, this);
				
				targetFound = true;
			}
			
			//send reject messages
			for (ResourceOfferEvent offerEvent : eviction.getResourceOffers()) {
				if (offerEvent != target) {
					RejectOfferAction rejectAction = new RejectOfferAction(offerEvent.getHostManager(), offerEvent);
					rejectAction.execute(simulation, this);
				}
			}
			
			//clear bid events
			eviction.getResourceOffers().clear();

		} 
		
		if (!targetFound) {
			/*
			 * 
			 * No hosts have spare capacity
			 * boot new host	
			 */
			
			Host target = null;
			VmStatus vmToMig = null;

			if (hostManager.isPowerStateListValid()) {
				ArrayList<Host> poweredOffHosts = new ArrayList<Host>();
				poweredOffHosts.addAll(hostManager.getPoweredOffHosts());
				
				//Sort Empty hosts in decreasing order by <power efficiency, power state>.
				Collections.sort(poweredOffHosts, HostComparator.getComparator(HostComparator.EFFICIENCY));
				Collections.reverse(poweredOffHosts);
				
				//Find target
				for (VmStatus vm : vmList) {
					for (Host host : poweredOffHosts) {
						if (canHost(host, vm)) {
							target = host;
							vmToMig = vm;
							break;
						}
					}
					
					if (target != null) {
						break;
					}
				}
			}
		
			if (target != null) {
				//trigger migration (MirationAction will boot host)
				SequentialManagementActionExecutor actions = new SequentialManagementActionExecutor();
				actions.addAction(new MigrationAction(manager, hostManager.getHost(), target, vmToMig.getId(), true));
				actions.addAction(new UpdatePowerStateListAction(target.getAutonomicManager(), hostManager.getPoweredOffHosts()));
				actions.execute(simulation, this);

				if (simulation.isRecordingMetrics())
					distributedMetrics.hostPowerOn++;
				
			} else {
				if (simulation.isRecordingMetrics())
					distributedMetrics.stressEventionFailed++;
			}

		}
		
		
		//clear eviction state
		hostManager.clearEviction();
		hostManager.setManagementState(ManagementState.NORMAL);
		
	}
	
	private void completeShutdownEviction(EvictionEvent event) {
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		Eviction eviction = hostManager.getEviction();
		
		//filter out VMs from the VM list that have completed execution since the eviction notice. Note, VMs are already sorted
		ArrayList<VmStatus> vmList = new ArrayList<VmStatus>();
		for (VmStatus vm : eviction.getVmList()) {
			if (hostManager.getHost().getVMAllocation(vm.getId()) != null) {
				vmList.add(vm);
			}
		}
		
		boolean cancelShutdown = true;
		
		//check for accepts
		if (eviction.getResourceOffers().size() > 0) {
			
			/*
			 * 
			 * Hosts have replied with spare capacity. Select a target for migration.
			 * 
			 */

			//Sort target hosts
			ArrayList<ResourceOfferEvent> targets = sortTargetHostsForShutdown(eviction.getResourceOffers());
			
			SequentialManagementActionExecutor actions = new SequentialManagementActionExecutor();
			SequentialManagementActionExecutor acceptActions = new SequentialManagementActionExecutor();	
			HostStatus hostStatus = new HostStatus(hostManager.getHost(), simulation.getSimulationTime());
			ArrayList<ResourceOfferEvent> acceptedOffers = new ArrayList<ResourceOfferEvent>();
			int count = 0;
			
			for (VmStatus vm : vmList) {
				for (ResourceOfferEvent hostOffer : targets) {
					Host offeringHost = hostOffer.getHost(); //to check capabilities. Can be assumed known by this host manager, since capabilities are static.
					HostStatus targetHostStatus = hostOffer.getHostStatus();
					Resources resourcesOffered = hostOffer.getResourcesOffered();
									
					//check capability & capacity
					if (canHost(offeringHost, targetHostStatus, vm) &&
							vm.getResourcesInUse().getCpu() <= resourcesOffered.getCpu() &&
							vm.getResourcesInUse().getMemory() <= resourcesOffered.getMemory() &&
							vm.getResourcesInUse().getBandwidth() <= resourcesOffered.getBandwidth() &&
							vm.getResourcesInUse().getStorage() <= resourcesOffered.getStorage()
							) {

						actions.addAction(new MigrationAction(manager, hostManager.getHost(), hostOffer.getHost(), vm.getId(), true));
						acceptedOffers.add(hostOffer);
						
						acceptActions.addAction(new AcceptOfferAction(hostOffer.getHostManager(), hostOffer));
						
						++count;
						
						//update working copies of host status and resource offers
						hostStatus.migrate(vm, targetHostStatus);
						resourcesOffered.setCpu(resourcesOffered.getCpu() - vm.getResourcesInUse().getCpu());
						resourcesOffered.setMemory(resourcesOffered.getMemory() - vm.getResourcesInUse().getMemory());
						resourcesOffered.setBandwidth(resourcesOffered.getBandwidth() - vm.getResourcesInUse().getBandwidth());
						resourcesOffered.setStorage(resourcesOffered.getStorage() - vm.getResourcesInUse().getStorage());
						
						break;
					}
				}
								
			}
			
			//verify that all vms will be migrated before triggering migrations
			if (count == vmList.size()) {		
				//add shutdown action
				actions.addAction(acceptActions);
				actions.addAction(new ShutdownHostAction(hostManager.getHost()));
				actions.execute(simulation, this);
			
				cancelShutdown = false;
				
				
				//determine remaining resources, recommend shutdown freeze duration
				
				//total remaining resources
				Resources remaining = new Resources();
				for (ResourceOfferEvent e : targets) {
					remaining.setCpu(remaining.getCpu() + e.getResourcesOffered().getCpu());
					remaining.setMemory(remaining.getMemory() + e.getResourcesOffered().getMemory());
					remaining.setBandwidth(remaining.getBandwidth() + e.getResourcesOffered().getBandwidth());
					remaining.setStorage(remaining.getStorage() + e.getResourcesOffered().getStorage());
				}
				
				Resources required = new Resources();
				for (VmStatus vm : eviction.getVmList()) {
					required.setCpu(vm.getResourcesInUse().getCpu());
					required.setMemory(vm.getResourcesInUse().getMemory());
					required.setBandwidth(vm.getResourcesInUse().getBandwidth());
					required.setStorage(vm.getResourcesInUse().getStorage());
				}
				
				if (remaining.getCpu() >= required.getCpu() &&
						remaining.getMemory() >= required.getMemory() &&
						remaining.getBandwidth() >= required.getBandwidth() &&
						remaining.getStorage() >= required.getStorage()) {
					
					hostManager.setShutdownResourcesAvailable(true);
					
				} else {
					hostManager.setShutdownResourcesAvailable(false);
				}
				
				
				
			} else {
				acceptedOffers.clear();
			}
			
			//send accept and reject messages
			for (ResourceOfferEvent e : targets) {
				
				if (acceptedOffers.contains(e)) {
					if (simulation.isRecordingMetrics())
						distributedMetrics.shutdownEviction++;
				} else {
					RejectOfferAction rejectAction = new RejectOfferAction(e.getHostManager(), e);
					rejectAction.execute(simulation, this);
				}
			}

			//clear eviction state
			hostManager.clearEviction();

		} 
		
		if (cancelShutdown) {
			/*
			 * 
			 * No hosts have spare capacity
			 * cancel the shut down
			 */
			
			//prevent repeated shutdown events
			hostManager.enactShutdownFreeze(simulation.getSimulationTime() + SHUTDOWN_FREEZE_AFTER_FAIL_DURATION);
			hostManager.setManagementState(ManagementState.NORMAL);
			hostManager.setShutdownState(ShutdownState.NONE);
			
			simulation.sendEvent(new ShutdownFailedEvent(hostManager.getBroadcastingGroup()));
			
			if (simulation.isRecordingMetrics())
				distributedMetrics.shutdownFailed++;

		}
	}
	
	/*
	 * 
	 * VM Offer Reply handling
	 * 
	 */
	public void execute(AcceptOfferEvent event) {
		
		if (distributedMetrics == null) {
			distributedMetrics = DistributedTestEnvironment.getDistributedMetrics(simulation);
		}
		
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
			
		hostManager.setManagementState(ManagementState.NORMAL);
		hostManager.clearCurrentOffer();
		
		//freeze eviction
		hostManager.enactEvictionFreeze(simulation.getSimulationTime() + EVICTION_FREEZE_DURATION);
		
		if (simulation.isRecordingMetrics())
			distributedMetrics.msgBasic++;
	}
	
	public void execute(RejectOfferEvent event) {
		
		if (distributedMetrics == null) {
			distributedMetrics = DistributedTestEnvironment.getDistributedMetrics(simulation);
		}
		
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		hostManager.setManagementState(ManagementState.NORMAL);
		hostManager.clearCurrentOffer();
		
		if (simulation.isRecordingMetrics())
			distributedMetrics.msgBasic++;
	}
	

	/**
	 * Receive a VM advertisement message
	 * 
	 * @param event
	 */
	public void execute(RequestResourcesEvent event) {	
		
		if (distributedMetrics == null) {
			distributedMetrics = DistributedTestEnvironment.getDistributedMetrics(simulation);
		}
		
		//received a VM advertisement
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		Host host = hostManager.getHost();
		HostStatus hostStatus = new HostStatus(hostManager.getHost(), simulation.getSimulationTime());
		
		if (simulation.isRecordingMetrics())
			distributedMetrics.receivedResourceRequest++;
		
		if (simulation.isRecordingMetrics())
			distributedMetrics.msgSingle++;
		
		//check if offers are frozen
		if (offersFrozen(hostManager) ||  manager == event.getHostManager()) {
			return;
		}
		
		//check for spare capacity
		if (hostManager.getManagementState() == ManagementState.NORMAL && hostManager.getShutdownState() == ShutdownState.NONE) {
			if (event.getHostManager() != manager &&					//ensure the event does not come from this manager
					!isStressed(hostManager) && 						//ensure this host is not stressed
					!(hostStatus.getIncomingMigrationCount() > 0) &&	//ensure there are no current migrations
					!(hostStatus.getOutgoingMigrationCount() > 0)) {

				Resources minResources = event.getMinResources();

				//check if we are capable of hosting this VM
				double avgCpu = getAvgCpu(hostManager, STRESS_MONITOR_WINDOW);
					
				Resources resourcesInUse = hostStatus.getResourcesInUse();
				
//				if (((hostStatus.getResourcesInUse().getCpu() + minResources.getCpu()) / host.getResourceManager().getTotalCpu() <= target) && //check current CPU util < target
//						((avgCpu + minResources.getCpu()) / host.getResourceManager().getTotalCpu() <= target) && //check average CPU util < target
//						(minResources.getMemory() <= (host.getResourceManager().getTotalMemory() - resourcesInUse.getMemory())) && //check memory
//						(minResources.getBandwidth() <= (host.getResourceManager().getTotalBandwidth() - resourcesInUse.getBandwidth())) && //check bandwidth
//						(minResources.getStorage() <= (host.getResourceManager().getTotalStorage() - resourcesInUse.getStorage())) ) //check storage
//				{ 
					
				//only check CPU, and if there is ANY memory available (we will consider this message to only contain CPU data)
				if (((hostStatus.getResourcesInUse().getCpu() + minResources.getCpu()) / host.getResourceManager().getTotalCpu() <= target) && //check current CPU util < target
						((avgCpu + minResources.getCpu()) / host.getResourceManager().getTotalCpu() <= target) && //check average CPU util < target
						(resourcesInUse.getMemory() < host.getResourceManager().getTotalMemory()) ) //check memory
				{ 
				
				
					//calculate resources to offer
					Resources resourcesOffered = new Resources();
					
					
					resourcesOffered.setCpu((int)(host.getResourceManager().getTotalCpu() * target) - resourcesInUse.getCpu());
					resourcesOffered.setMemory(host.getResourceManager().getTotalMemory() - resourcesInUse.getMemory());
					resourcesOffered.setBandwidth(host.getResourceManager().getTotalBandwidth() - resourcesInUse.getBandwidth());
					resourcesOffered.setStorage(host.getResourceManager().getTotalStorage() - resourcesInUse.getStorage());
					
					//send accept message
					ResourceOfferEvent offer = new ResourceOfferEvent(event.getHostManager(), event.getEviction(), host, manager, hostStatus, resourcesOffered);
					simulation.sendEvent(offer);
					hostManager.setManagementState(ManagementState.OFFERING);
					hostManager.setCurrentOffer(offer);
				}

			}
		}
	}
	
	/**
	 * Receive a VM accept message
	 * 
	 * @param event
	 */
	public void execute(ResourceOfferEvent event) {
		
		if (distributedMetrics == null) {
			distributedMetrics = DistributedTestEnvironment.getDistributedMetrics(simulation);
		}
		
		//a Host has accepted your advertised VM
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		//ensure that this VM is still being advertised
		if ((hostManager.getManagementState() == ManagementState.EVICTING) && hostManager.getEviction().equals(event.getEviction())) {
			hostManager.getEviction().getResourceOffers().add(event);
			
		} else {
			//send rejection message
			simulation.sendEvent(new RejectOfferEvent(event.getHostManager(), event));
		}
		
		if (simulation.isRecordingMetrics())
			distributedMetrics.msgResource++;

	}
	
	/**
	 * Receive a Host Shutting Down event, indicating that another host has shut itself down
	 * 
	 * @param event
	 */
	public void execute(HostShuttingDownEvent event) {
		
		if (distributedMetrics == null) {
			distributedMetrics = DistributedTestEnvironment.getDistributedMetrics(simulation);
		}
		
		//store in list of powered off hosts
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		if (event.getHost() != hostManager.getHost())
			hostManager.getPoweredOffHosts().add(event.getHost());
		
		//set shutdown freeze, if not set already
		if (!shutdownFrozen(hostManager)) {
			if (event.areShutdownResourcesAvailabe()) {
				
				hostManager.enactShutdownFreeze(simulation.getSimulationTime() + SHUTDOWN_SUBSEQUENT_FREEZE_DURATION);
			} else {

				hostManager.enactShutdownFreeze(simulation.getSimulationTime() + SHUTDOWN_FREEZE_AFTER_FAIL_DURATION);
			}
		}

		if (simulation.isRecordingMetrics())
			distributedMetrics.receivedPowerStateMessage++;
		
		if (simulation.isRecordingMetrics())
			distributedMetrics.msgBasic++;

	}
	
	/**
	 * Receive a Host Power On event, indicating that another host has powered on
	 * 
	 * @param event
	 */
	public void execute(HostPowerOnEvent event) {
		
		if (distributedMetrics == null) {
			distributedMetrics = DistributedTestEnvironment.getDistributedMetrics(simulation);
		}
		
		//remove from list of powered off hosts
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		hostManager.getPoweredOffHosts().remove(event.getHost());
		
		if (simulation.isRecordingMetrics())
			distributedMetrics.receivedPowerStateMessage++;
		
		if (simulation.isRecordingMetrics())
			distributedMetrics.msgBasic++;
		
	}
	
	public void execute(UpdatePowerStateListEvent event) {
		
		if (distributedMetrics == null) {
			distributedMetrics = DistributedTestEnvironment.getDistributedMetrics(simulation);
		}
		
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		
		hostManager.getPoweredOffHosts().clear();
		
		for (Host host : event.getPoweredOffHosts()) {
			hostManager.getPoweredOffHosts().add(host);
		}
		
		hostManager.setPowerStateListValid(true);
	}

	
	/**
	 * Determine if the host is 'stressed'
	 * 
	 * @param hostManager
	 * @param hostStatus
	 * @return
	 */
	private boolean isStressed(HostManagerBroadcast hostManager) {
		double util = getAvgCpuUtil(hostManager, STRESS_MONITOR_WINDOW);

		if (util > upper) {
			return true;
		}
		return false;
	}
	
	/**
	 * Determine if the host is 'underutilized'
	 * 
	 * @param hostManager
	 * @param hostStatus
	 * @return
	 */
	private boolean isUnderUtilized(HostManagerBroadcast hostManager, HostStatus hostStatus) {
		if (getAvgCpuUtil(hostManager, UNDER_MONITOR_WINDOW) < lower) {
			return true;
		}
		return false;
	}
	

	private double getAvgCpuUtil(HostManagerBroadcast hostManager, int windowSize) {
		double avgCpu = 0;
		int count = 0;
		
		for (HostStatus status : hostManager.getHistory()) {
			++count;
			avgCpu += status.getResourcesInUse().getCpu() / hostManager.getHost().getTotalCpu();
			
			if (count == windowSize)
				break;
		}
		avgCpu = avgCpu / count;

		return avgCpu;
	}
	
	private double getAvgCpu(HostManagerBroadcast hostManager, int windowSize) {
		double avgCpu = 0;
		int count = 0;
			
		for (HostStatus status : hostManager.getHistory()) {
			++count;
			avgCpu += status.getResourcesInUse().getCpu();
			
			if (count == windowSize)
				break;
		}
		avgCpu = avgCpu / count;

		return avgCpu;
	}
	
	@SuppressWarnings("unused")
	private double getCpuUtil(HostManagerBroadcast hostManager) {
		if (hostManager.getHistory().size() > 0) {
			return hostManager.getHistory().get(0).getResourcesInUse().getCpu() / hostManager.getHost().getTotalCpu();
		}
		
		return 0;
	}
	
	
	private Resources getMinResources(ArrayList<VmStatus> vmList) {
		
		Resources minResources = new Resources();
		
		for (VmStatus vm : vmList) {
			
			if (minResources.getCpu() == 0 || vm.getResourcesInUse().getCpu() < minResources.getCpu()) 
				minResources.setCpu(vm.getResourcesInUse().getCpu());
			if (minResources.getMemory() == 0 || vm.getResourcesInUse().getMemory() < minResources.getMemory()) 
				minResources.setMemory(vm.getResourcesInUse().getMemory());
			if (minResources.getBandwidth() == 0 || vm.getResourcesInUse().getBandwidth() < minResources.getBandwidth())
				minResources.setBandwidth(vm.getResourcesInUse().getBandwidth());
			if (minResources.getStorage() == 0 || vm.getResourcesInUse().getStorage() < minResources.getStorage()) 
				minResources.setStorage(vm.getResourcesInUse().getStorage());
		}
		
		return minResources;
	}
	
	/**
	 * Sorts Partially-utilized and Underutilized hosts in decreasing order by 
	 * <power efficiency, CPU utilization>.
	 */
	private ArrayList<ResourceOfferEvent> sortTargetHostsForShutdown(ArrayList<ResourceOfferEvent> targets) {
		ArrayList<ResourceOfferEvent> sorted = new ArrayList<ResourceOfferEvent>();
		
		//classify into partially utilized and under utilized
		ArrayList<ResourceOfferEvent> partiallyUtilized = new ArrayList<ResourceOfferEvent>();
		ArrayList<ResourceOfferEvent> underUtilized = new ArrayList<ResourceOfferEvent>();
		
		// Sort Partially-utilized and Underutilized hosts in decreasing order by <power efficiency, CPU utilization>.
		sorted.addAll(partiallyUtilized);
		sorted.addAll(underUtilized);
		Collections.sort(sorted, ResourceOfferComparator.getComparator(ResourceOfferComparator.EFFICIENCY, ResourceOfferComparator.CPU_UTIL));
		Collections.reverse(sorted);
		
		return targets;
	}
	
	/**
	 * Sorts Partially-Utilized hosts in increasing order by <CPU utilization, 
	 * power efficiency>, Underutilized hosts in decreasing order by 
	 * <CPU utilization, power efficiency>, and Empty hosts in decreasing 
	 * order by <power efficiency, power state>.
	 * 
	 * Returns Partially-utilized, Underutilized, and Empty hosts, in that 
	 * order.
	 */
	private ArrayList<ResourceOfferEvent> sortTargetHostsForStressEviction(ArrayList<ResourceOfferEvent> targets) {
		ArrayList<ResourceOfferEvent> sorted = new ArrayList<ResourceOfferEvent>();
		
		//classify into partially utilized and under utilized
		ArrayList<ResourceOfferEvent> partiallyUtilized = new ArrayList<ResourceOfferEvent>();
		ArrayList<ResourceOfferEvent> underUtilized = new ArrayList<ResourceOfferEvent>();
		
		for (ResourceOfferEvent host : targets) {
			//note that stressed and powered off hosts do not bid, which represents a difference from the centralized implementation
			if (host.getHostStatus().getResourcesInUse().getCpu() / host.getHost().getResourceManager().getTotalCpu() < lower) {
				underUtilized.add(host);
			} else {
				partiallyUtilized.add(host);
			}
		}
		
		// Sort Partially-Utilized hosts in increasing order by <CPU in use, power efficiency>.
		Collections.sort(partiallyUtilized, ResourceOfferComparator.getComparator(ResourceOfferComparator.CPU_IN_USE, ResourceOfferComparator.EFFICIENCY));
		
		// Sort Underutilized hosts in decreasing order by <CPU in use, power efficiency>.
		Collections.sort(underUtilized, ResourceOfferComparator.getComparator(ResourceOfferComparator.CPU_IN_USE, ResourceOfferComparator.EFFICIENCY));
		Collections.reverse(underUtilized);
		
		sorted.addAll(partiallyUtilized);
		sorted.addAll(underUtilized);
		
		return sorted;
	}
	
	/**
	 * Order VMs for eviction, in the case this host is 'stressed'
	 * 
	 * @param vms
	 * @param host
	 * @return
	 */
	private ArrayList<VmStatus> orderVmsStressed(ArrayList<VmStatus> vms, Host host) {
		ArrayList<VmStatus> sorted = new ArrayList<VmStatus>();
		
		// Remove VMs with less CPU load than the CPU load by which the
		// host is stressed.
		double cpuExcess = host.getResourceManager().getCpuInUse() - host.getTotalCpu() * upper;
		for (VmStatus vm : vms)
			if (vm.getResourcesInUse().getCpu() >= cpuExcess)
				sorted.add(vm);
		
		if (!sorted.isEmpty())
			// Sort VMs in increasing order by CPU load.
			Collections.sort(sorted, VmStatusComparator.getComparator(VmStatusComparator.CPU_IN_USE));
		else {
			// Add original list of VMs and sort them in decreasing order by 
			// CPU load, so as to avoid trying to migrate the smallest VMs 
			// first (which would not help resolve the stress situation).
			sorted.addAll(vms);
			Collections.sort(sorted, VmStatusComparator.getComparator(VmStatusComparator.CPU_IN_USE));
			Collections.reverse(sorted);
		}
		
		return sorted;
	}
	
	/**
	 * Order VMs for evict, in the case this host is 'underutilized'
	 * 
	 * @param vms
	 * @return
	 */
	private ArrayList<VmStatus> orderVmsUnderUtilized(ArrayList<VmStatus> vms) {
		ArrayList<VmStatus> sources = new ArrayList<VmStatus>(vms);
		
		// Sort VMs in decreasing order by <overall capacity, CPU load>.
		// (Note: since CPU can be oversubscribed, but memory can't, memory 
		// takes priority over CPU when comparing VMs by _size_ (capacity).)
		Collections.sort(sources, VmStatusComparator.getComparator(
				VmStatusComparator.MEMORY, 
				VmStatusComparator.CPU_CORES, 
				VmStatusComparator.CORE_CAP, 
				VmStatusComparator.CPU_IN_USE));
		Collections.reverse(sources);
		
		return sources;
	}
	
	/**
	 * Determine if this host is capable of hosting the given VM
	 * 
	 * @param host
	 * @param hostStatus
	 * @param vm
	 * @return
	 */
	private boolean canHost(Host host, HostStatus hostStatus, VmStatus vm) {
		//check capabilities
		if (host.getCpuCount() * host.getCoreCount() < vm.getCores() ||
				host.getCoreCapacity() < vm.getCoreCapacity()) {
			return false;
		}
		
		//check remaining capacity
		Resources resourcesInUse = hostStatus.getResourcesInUse();
		if (host.getResourceManager().getTotalCpu() - resourcesInUse.getCpu() < vm.getResourcesInUse().getCpu())
			return false;
		if (host.getResourceManager().getTotalMemory() - resourcesInUse.getMemory() < vm.getResourcesInUse().getMemory())
			return false;
		if (host.getResourceManager().getTotalBandwidth() - resourcesInUse.getBandwidth() < vm.getResourcesInUse().getBandwidth())
			return false;
		if (host.getResourceManager().getTotalStorage() - resourcesInUse.getStorage() < vm.getResourcesInUse().getStorage())
			return false;

		return true;
	}
	
	private boolean canHost(Host host, VmStatus vm) {
		//check capabilities
		if (host.getCpuCount() * host.getCoreCount() < vm.getCores() ||
				host.getCoreCapacity() < vm.getCoreCapacity()) {
			return false;
		}
		
		//check capacity
		if (host.getResourceManager().getTotalCpu() < vm.getResourcesInUse().getCpu())
			return false;
		if (host.getResourceManager().getTotalMemory() < vm.getResourcesInUse().getMemory())
			return false;
		if (host.getResourceManager().getTotalBandwidth() < vm.getResourcesInUse().getBandwidth())
			return false;
		if (host.getResourceManager().getTotalStorage() < vm.getResourcesInUse().getStorage())
			return false;
		
		return true;
	}
	
	private long getActiveHostCount(HostManagerBroadcast hostManager) {
		return hostManager.getGroupSize() - hostManager.getPoweredOffHosts().size();
	}
	
	public boolean offersFrozen(HostManagerBroadcast hostManager) {
		if (hostManager.offersFrozen()) {
			if (simulation.getSimulationTime() >= hostManager.getOfferFreezeExpiry()) {
				hostManager.expireOfferFreeze();
			}
		}
		return hostManager.offersFrozen();
	}
	
	public boolean evictionFrozen(HostManagerBroadcast hostManager) {
		if(hostManager.evictionFrozen()) {
			if (simulation.getSimulationTime() >= hostManager.getEvictionFreezeExpiry()) {
				hostManager.expireEvictionFreeze();
			}
		}
		return hostManager.evictionFrozen();
	}
	
	public boolean shutdownFrozen(HostManagerBroadcast hostManager) {
		if (hostManager.shutdownFrozen()) {
			if (simulation.getSimulationTime() >= hostManager.getShutdownFreezeExpiry()) {
				hostManager.expireShutdownFreeze();
			}
		}
		return hostManager.shutdownFrozen();
	}
	
	@Override
	public void onInstall() {

	}

	@Override
	public void onManagerStart() {
		//send power on message
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);

		hostManager.setManagementState(ManagementState.NORMAL);
		hostManager.setShutdownState(ShutdownState.NONE);
		hostManager.getHistory().clear(); //clear monitoring history
		hostManager.enactShutdownFreeze(simulation.getSimulationTime() + SHUTDOWN_FREEZE_AFTER_BOOT_DURATION);
		hostManager.setPowerStateListValid(false);
		
		simulation.sendEvent(new HostPowerOnEvent(hostManager.getBroadcastingGroup(), hostManager.getHost())); 
	}

	@Override
	public void onManagerStop() {
		//send shutdown message
		HostManagerBroadcast hostManager = manager.getCapability(HostManagerBroadcast.class);
		simulation.sendEvent(new HostShuttingDownEvent(hostManager.getBroadcastingGroup(), hostManager.getHost(), hostManager.areShutdownResourcesvailable()));
	}

}


