package edu.uwo.csd.dcsim.core.metrics;

import java.util.Comparator;

public class MetricAlphaComparator implements Comparator<AbstractMetric> {

	@Override
	public int compare(AbstractMetric arg0, AbstractMetric arg1) {
		return arg0.getName().compareTo(arg1.getName());
	}
	

}
