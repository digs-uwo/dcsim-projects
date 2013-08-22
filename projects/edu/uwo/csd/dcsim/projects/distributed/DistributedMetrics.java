package edu.uwo.csd.dcsim.projects.distributed;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim.common.Tuple;
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
		out.info("   stressEvictionFailed: " + stressEventionFailed);
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

	@Override
	public List<Tuple<String, Object>> getMetricValues() {
		List<Tuple<String, Object>> metrics = new ArrayList<Tuple<String, Object>>();
		
		metrics.add(new Tuple<String, Object>("stressEvictionFailed", stressEventionFailed));
		metrics.add(new Tuple<String, Object>("shutdownFailed", shutdownFailed));
		metrics.add(new Tuple<String, Object>("stressEvict", stressEvict));
		metrics.add(new Tuple<String, Object>("shutdownEviction", shutdownEviction));
		metrics.add(new Tuple<String, Object>("shutdownTriggered", shutdownTriggered));
		metrics.add(new Tuple<String, Object>("hostPowerOn", hostPowerOn));
		
		metrics.add(new Tuple<String, Object>("receivedResourceRequest", receivedResourceRequest));
		metrics.add(new Tuple<String, Object>("receivedPowerStateMessage", receivedPowerStateMessage));
		metrics.add(new Tuple<String, Object>("msgResource", msgResource));
		metrics.add(new Tuple<String, Object>("msgBasic", msgBasic));
		metrics.add(new Tuple<String, Object>("msgSingle", msgSingle));
		
		metrics.add(new Tuple<String, Object>("servicesReceived", servicesReceived));
		metrics.add(new Tuple<String, Object>("servicesPlaced", servicesPlaced));
		metrics.add(new Tuple<String, Object>("servicePlacementsFailed", servicePlacementsFailed));

		return metrics;
	}

}
