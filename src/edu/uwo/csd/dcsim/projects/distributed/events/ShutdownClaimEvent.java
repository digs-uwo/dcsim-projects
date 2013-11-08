package edu.uwo.csd.dcsim.projects.distributed.events;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.host.Host;
import edu.uwo.csd.dcsim.host.Resources;
import edu.uwo.csd.dcsim.management.AutonomicManager;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class ShutdownClaimEvent extends MessageEvent {

	private AutonomicManager candidateAM;
	private Host candidateHost;
	private Resources resourcesInUse;
	private int vmCount;
	
	public ShutdownClaimEvent(SimulationEventListener target, AutonomicManager candidateAM, Host candidateHost, Resources resourcesInUse, int vmCount) {
		super(target);
		
		this.candidateAM = candidateAM;
		this.candidateHost = candidateHost;
		this.resourcesInUse = resourcesInUse;
		this.vmCount  = vmCount;
	}
	
	public AutonomicManager getCandidateAM() {
		return candidateAM;
	}
	
	public Host getCandidateHost() {
		return candidateHost;
	}
	
	public Resources getResourcesInUse() {
		return resourcesInUse;
	}
	
	public int getVmCount() {
		return vmCount;
	}

}
