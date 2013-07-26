package edu.uwo.csd.dcsim.projects.hierarchical.policies;

import edu.uwo.csd.dcsim.core.SimulationEventListener;
import edu.uwo.csd.dcsim.management.Policy;
import edu.uwo.csd.dcsim.projects.hierarchical.capabilities.RackPoolManager;
import edu.uwo.csd.dcsim.projects.hierarchical.events.MigRequestEvent;

/**
 * 
 * 
 * @author Gaston Keller
 *
 */
public abstract class VmRelocationPolicyLevel1 extends Policy {

	protected SimulationEventListener target;
	
	/**
	 * Creates an instance of VmRelocationPolicyLevel1.
	 */
	public VmRelocationPolicyLevel1(SimulationEventListener target) {
		addRequiredCapability(RackPoolManager.class);
		
		this.target = target;
	}
	
	/**
	 * 
	 */
	public void execute(MigRequestEvent event) {
		// TODO
		
		
		// get RackData objects
		
		// sort Racks in decreasing order by max spare capacity
		// select the first Rack with enough resources to host the VM
		// if fails, break (following Racks have even less resources)
		
		// Note: Personally, I feel that choosing a suspended Host over a powered off Host makes sense.
		// Therefore, choose the most loaded Rack (i.e., less number of inactive servers) that has a 
		// suspended Host (at least one -- you only need one after all). If no Rack in the Cluster has 
		// a suspended Host, then power on a Host in the most loaded Rack.
		
		// sort Racks in increasing order by # of inactive Hosts (suspended + powered off)] Hosts  ???
		
		// if we have Rack A with (1,1) and Rack B with (2,0) -- suspended and powered off, respectively --
		// which Rack should we choose?
		
		
		// NEWS REPORT!!!  Right now, the system only deals with ON and OFF Hosts; the SUSPENDED state 
		// is not currently in use...
		// ... so... how does this affect RackStatus (and ClusterStatus?) and this method?
		
		
		
		
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
