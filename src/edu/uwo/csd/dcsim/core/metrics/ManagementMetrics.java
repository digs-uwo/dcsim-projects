package edu.uwo.csd.dcsim.core.metrics;

import java.io.PrintStream;
import java.util.*;
import java.util.Map.Entry;

import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.management.events.MessageEvent;

public class ManagementMetrics extends MetricCollection {

	public ManagementMetrics(Simulation simulation) {
		super(simulation);
		// TODO Auto-generated constructor stub
	}

	Map<Class<? extends MessageEvent>, Long> messageCount = new HashMap<Class<? extends MessageEvent>, Long>();
	Map<Class<? extends MessageEvent>, Double> messageBw = new HashMap<Class<? extends MessageEvent>, Double>();
	Map<Class<?>, Long> migrationCount = new HashMap<Class<?>, Long>();
	long placements;
	
	public void addMessage(MessageEvent message) {
		
		long count = 0;
		double bw = 0;
		if (messageCount.containsKey(message.getClass())) {
			count = messageCount.get(message.getClass());
			bw = messageBw.get(message.getClass());
		}
		++count;
		bw += message.getMessageSize();
		
		messageCount.put(message.getClass(), count);
		messageBw.put(message.getClass(), bw);
		
	}
	
	public void addMigration(Class<?> triggeringClass) {
		long count = 0;
		
		if (migrationCount.containsKey(triggeringClass)) {
			count = migrationCount.get(triggeringClass);
		}
		++count;
		
		migrationCount.put(triggeringClass, count);
	}
	
	public Map<Class<? extends MessageEvent>, Long> getMessageCount() {
		return messageCount;
	}
	
	public double getTotalMessageCount() {
		double total = 0;
		
		for (long l : messageCount.values()) {
			total += l;
		}
		
		return total;
	}
	
	public Map<Class<? extends MessageEvent>, Double> getMessageBw() {
		return messageBw;
	}
	
	public double getTotalMessageBw() {
		double total = 0;
		
		for (Double d : messageBw.values()) {
			total += d;
		}
		
		return total;
	}
	
	public Map<Class<?>, Long> getMigrationCount() {
		return migrationCount;
	}
	
	public double getTotalMigrationCount() {
		double total = 0;
		
		for (Long l : migrationCount.values()) {
			total += l;
		}
		
		return total;
	}

	@Override
	public void completeSimulation() {
		
	}

	@Override
	public void printDefault(PrintStream out) {
		out.println("-- MANAGEMENT --");
		out.println("Messages");
		for (Entry<Class<? extends MessageEvent>, Long> entry : getMessageCount().entrySet()) {
			out.println("    " + entry.getKey().getName() + ": " + entry.getValue());
		}
		out.println("Message BW");
		for (Entry<Class<? extends MessageEvent>, Double> entry : getMessageBw().entrySet()) {
			out.println("    " + entry.getKey().getName() + ": " + entry.getValue());
		}
		out.println("Migrations");
		for (Entry<Class<?>, Long> entry : getMigrationCount().entrySet()) {
			out.println("    " + entry.getKey().getName() + ": " + entry.getValue());
		}
	}
	
}
