package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.management.events.VmPlacementEvent;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.ClusterPoolManager;

/**
 * ...
 * 
 * @author Gaston Keller
 *
 */
public class VmPlacementPolicyLevel2 extends Policy {

	/**
	 * Creates an instance of VmPlacementPolicyLevel2.
	 */
	public VmPlacementPolicyLevel2() {
		addRequiredCapability(ClusterPoolManager.class);
	}
	
	/**
	 * ...
	 */
	public void execute(VmPlacementEvent event) {
		
		// TODO
		
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
