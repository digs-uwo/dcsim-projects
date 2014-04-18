package edu.uwo.csd.dcsim.projects.overloadProbability.policies;

import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.management.capabilities.HostPoolManager;
import edu.uwo.csd.dcsim.management.events.HostStatusEvent;
import edu.uwo.csd.dcsim.projects.overloadProbability.capabilities.VmMarkovChainManager;

public class VmMarkovChainUpdatePolicy extends Policy {
	
	public VmMarkovChainUpdatePolicy() {
		addRequiredCapability(HostPoolManager.class);
		addRequiredCapability(VmMarkovChainManager.class);
	}

	public void execute(HostStatusEvent event) {		
		HostPoolManager hostPool = manager.getCapability(HostPoolManager.class);
		VmMarkovChainManager mcMan = manager.getCapability(VmMarkovChainManager.class);
		
		mcMan.updateHost(hostPool.getHost(event.getHostStatus().getId()), simulation);
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
