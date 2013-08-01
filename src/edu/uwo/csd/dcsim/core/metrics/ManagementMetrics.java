package edu.uwo.csd.dcsim.core.metrics;

import java.util.*;

import org.apache.commons.math3.stat.descriptive.summary.Sum;

import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class ManagementMetrics extends MetricCollection {

	public ManagementMetrics(Simulation simulation) {
		super(simulation);
		// TODO Auto-generated constructor stub
	}

	Map<Class<? extends MessageEvent>, WeightedMetric> messageCount = new HashMap<Class<? extends MessageEvent>, WeightedMetric>();
	Map<Class<? extends MessageEvent>, WeightedMetric> messageBw = new HashMap<Class<? extends MessageEvent>, WeightedMetric>();
	Map<Class<?>, WeightedMetric> migrationCount = new HashMap<Class<?>, WeightedMetric>();
	
	public void addMessage(MessageEvent message) {
		double count = 0;
		double bw = 0;
		if (messageCount.containsKey(message.getClass())) {
			count = messageCount.get(message.getClass()).getValues().get(0);
			messageCount.get(message.getClass()).getValues().remove(0);
			bw = messageBw.get(message.getClass()).getValues().get(0);
			messageBw.get(message.getClass()).getValues().remove(0);
		} else {
			messageCount.put(message.getClass(), new WeightedMetric(this));
			messageBw.put(message.getClass(), new WeightedMetric(this));
		}
		++count;
		bw += message.getMessageSize();
		
		messageCount.get(message.getClass()).getValues().add(count);
		messageBw.get(message.getClass()).getValues().add(bw);
		
	}
	
	public void addMigration(Class<?> triggeringClass) {
		double count = 0;
		if (migrationCount.containsKey(triggeringClass)) {
			count = migrationCount.get(triggeringClass).getValues().get(0);
			migrationCount.get(triggeringClass).getValues().remove(0);
		} else {
			migrationCount.put(triggeringClass, new WeightedMetric(this));
		}
		++count;
		
		migrationCount.get(triggeringClass).getValues().add(count);
	}
	
	public Map<Class<? extends MessageEvent>, WeightedMetric> getMessageCount() {
		return messageCount;
	}
	
	public double getTotalMessageCount() {
		double total = 0;
		
		for (WeightedMetric m : messageCount.values()) {
			Sum sum = new Sum();
			total += sum.evaluate(m.toDoubleArray());
		}
		
		return total;
	}
	
	public Map<Class<? extends MessageEvent>, WeightedMetric> getMessageBw() {
		return messageBw;
	}
	
	public double getTotalMessageBw() {
		double total = 0;
		
		for (WeightedMetric m : messageBw.values()) {
			Sum sum = new Sum();
			total += sum.evaluate(m.toDoubleArray());
		}
		
		return total;
	}
	
	public Map<Class<?>, WeightedMetric> getMigrationCount() {
		return migrationCount;
	}
	
	public double getTotalMigrationCount() {
		double total = 0;
		
		for (WeightedMetric m : migrationCount.values()) {
			Sum sum = new Sum();
			total += sum.evaluate(m.toDoubleArray());
		}
		
		return total;
	}

	@Override
	public void completeSimulation() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void completeTimeStep() {
		updateTimeWeights();
	}
	
}
