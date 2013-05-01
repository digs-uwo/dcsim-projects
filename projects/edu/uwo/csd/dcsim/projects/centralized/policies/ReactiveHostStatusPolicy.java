package edu.uwo.csd.dcsim.projects.centralized.policies;

import edu.uwo.csd.dcsim.management.events.HostStatusEvent;
import edu.uwo.csd.dcsim.management.policies.HostStatusPolicy;
import edu.uwo.csd.dcsim.projects.centralized.events.VmRelocationEvent;

public class ReactiveHostStatusPolicy extends HostStatusPolicy {

	public ReactiveHostStatusPolicy(int windowSize) {
		super(windowSize);
	}

	/**
	 * Updates the corresponding Host's status and invokes the VM Relocation 
	 * policy to perform a Stress Check. 
	 */
	public void execute(HostStatusEvent event) {
		super.execute(event);
		
		// Send event to trigger VM Relocation policy (for this host).
		simulation.sendEvent(new VmRelocationEvent(manager, event.getHostStatus().getId()));
	}

}
