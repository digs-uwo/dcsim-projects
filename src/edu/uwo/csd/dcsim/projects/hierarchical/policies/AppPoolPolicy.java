package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.events.VmInstantiationCompleteEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.AppData;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.AppPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.events.IncomingMigrationEvent;

public class AppPoolPolicy extends Policy {

	public AppPoolPolicy() {
		addRequiredCapability(AppPoolManager.class);
	}
	
	public void execute(IncomingMigrationEvent event) {
		
		simulation.getLogger().debug(String.format("[AppPool] Processing IncomingMigrationEvent for App #%d.", event.getApplication().getId()));
		
		AppPoolManager appPool = manager.getCapability(AppPoolManager.class);
		AppData incomingApp = event.getApplication();
		AppData localApp = appPool.getApplication(incomingApp.getId());
		
		if (null == localApp)
			appPool.addApplication(incomingApp);
		else {			// The application already exists locally, so we need to figure out who is who (i.e., who is master and who is a surrogate) to decide how to proceed.
			if (incomingApp.isMaster()) {
				
				if (localApp.isMaster())
					throw new RuntimeException(String.format("[AppPool] Found local application and both are masters!"));
				
				incomingApp.mergeSurrogate(localApp, manager);
				appPool.addApplication(incomingApp);
			}
			else {		// Incoming application is a surrogate.
				localApp.mergeSurrogate(incomingApp, event.getOrigin());
			}
		}
	}
	
	public void execute(VmInstantiationCompleteEvent event) {
		
		simulation.getLogger().debug(String.format("[AppPool] Processing VmInstantiationCompleteEvent for VM #%d in Host #%d.", event.getVmId(), event.getHostId()));
		
		manager.getCapability(AppPoolManager.class).getApplication(event.getApplicationId()).getTask(event.getTaskId()).setHostingVm(event.getInstanceId(), event.getVmId());
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
