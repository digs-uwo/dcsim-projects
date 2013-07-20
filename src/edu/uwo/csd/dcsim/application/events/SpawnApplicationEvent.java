package edu.uwo.csd.dcsim.application.events;

import edu.uwo.csd.dcsim.application.ApplicationGenerator;
import edu.uwo.csd.dcsim.core.Event;

public class SpawnApplicationEvent extends Event {

	int currentRate;
	
	public SpawnApplicationEvent(ApplicationGenerator serviceProducer, int currentRate) {
		super(serviceProducer);
		
		this.currentRate = currentRate;
	}
	
	public int getCurrentRate() {
		return currentRate;
	}

}
