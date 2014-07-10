package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.events.MigrationCompleteEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.MigrationTrackingManager;

public class MigrationTrackingPolicy extends Policy {

	public MigrationTrackingPolicy() {
		addRequiredCapability(MigrationTrackingManager.class);
		
	}
	
	public void execute(MigrationCompleteEvent event) {
		
		simulation.getLogger().debug(String.format("Migration of VM #%d completed from Host #%d to Host #%d.",
				event.getVmId(),
				event.getSourceHostId(),
				event.getTargetHostId()));
		
		manager.getCapability(MigrationTrackingManager.class).removeMigratingVm(event.getVmId());
	}
	
	@Override
	public void onInstall() {
		// Auto-generated method stub
	}
	
	@Override
	public void onManagerStart() {
		// Auto-generated method stub
	}
	
	@Override
	public void onManagerStop() {
		// Auto-generated method stub
	}

}
