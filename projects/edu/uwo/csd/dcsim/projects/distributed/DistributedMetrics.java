package edu.uwo.csd.dcsim.projects.distributed;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.core.metrics.MetricCollection;

public class DistributedMetrics extends MetricCollection {
	
	public long stressEventionFailed = 0;
	public long shutdownFailed = 0;
	public long stressEvict = 0;
	public long shutdownEviction = 0;
	public long shutdownTriggered = 0;
	
	public long receivedResourceRequest = 0;
	public long receivedPowerStateMessage = 0;
	public long msgResource = 0;
	public long msgBasic = 0;
	public long msgSingle = 0;
	
	public long hostPowerOn = 0;
	public long servicesReceived = 0;
	public long servicesPlaced = 0;
	public long servicePlacementsFailed = 0;
	
	public DistributedMetrics(Simulation simulation) {
		super(simulation);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void completeSimulation() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void printDefault(Logger out) {
		out.info("-- DISTRIBUTED --");
		out.info("Management Actions");
		out.info("   stressEventionFailed: " + stressEventionFailed);
		out.info("   shutdownFailed: " + shutdownFailed);
		out.info("   stressEvict: " + stressEvict);
		out.info("   shutdownEviction: " + shutdownEviction);
		out.info("   shutdownTriggered: " + shutdownTriggered);
		out.info("   hostPowerOn: " + hostPowerOn);
		out.info("Messaging");
		out.info("   receivedResourceRequest: " + receivedResourceRequest);
		out.info("   receivedPowerStateMessage: " + receivedPowerStateMessage);
		out.info("   msgResource: " + msgResource);
		out.info("   msgBasic: " + msgBasic);
		out.info("   msgSingle: " + msgSingle);
		out.info("Placement");
		out.info("   servicesReceived: " + servicesReceived);
		out.info("   servicesPlaced: " + servicesPlaced);
		out.info("   servicePlacementsFailed: " + servicePlacementsFailed);
		
	}

}
