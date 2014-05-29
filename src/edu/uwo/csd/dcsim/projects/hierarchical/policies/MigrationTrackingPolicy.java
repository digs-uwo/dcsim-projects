package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.events.MigrationCompleteEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.MigrationTrackingManager;

public class MigrationTrackingPolicy extends Policy {

	public MigrationTrackingPolicy() {
		addRequiredCapability(MigrationTrackingManager.class);
		
	}
	
	public void execute(MigrationCompleteEvent event) {
		MigrationTrackingManager ongoingMigs = manager.getCapability(MigrationTrackingManager.class);
		
		if (!ongoingMigs.removeMigratingVm(event.getVmId()))
			throw new RuntimeException(String.format("Migration of VM #%d completed, but the migration was NOT registered as actually happening.", event.getVmId()));
		
		simulation.getLogger().debug(String.format("Migration of VM #%d completed from Host #%d to Host #%d.",
				event.getVmId(),
				event.getSourceHostId(),
				event.getTargetHostId()));
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
