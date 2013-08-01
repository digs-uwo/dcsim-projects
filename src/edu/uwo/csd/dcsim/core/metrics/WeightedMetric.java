package edu.uwo.csd.dcsim.core.metrics;

import java.util.ArrayList;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.apache.commons.math3.stat.descriptive.rank.Max;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.stat.descriptive.rank.Min;
import org.apache.commons.math3.stat.descriptive.summary.Sum;

public class WeightedMetric {

	MetricCollection metricCollection;
	ArrayList<Double> values = new ArrayList<Double>();
	
	public WeightedMetric(MetricCollection metricCollection) {
		this.metricCollection = metricCollection;
	}
	
	public void add(double val) {
		values.add(val);
	}
	
	public ArrayList<Double> getValues() {
		return values;
	}
	
	public double[] toDoubleArray() {
		double[] array = new double[values.size()];
		
		for (int i = 0; i < values.size(); ++i) {
			array[i] = values.get(i);
		}
		
		return array;
	}
	
	public double getMean() {
		Mean mean = new Mean();
		return mean.evaluate(toDoubleArray(), metricCollection.toDoubleArray(metricCollection.getTimeWeights()));
	}
	
	public double getVariance() {
		Variance variance = new Variance();
		return variance.evaluate(toDoubleArray(), metricCollection.toDoubleArray(metricCollection.getTimeWeights()));
	}
	
	public double getSum() {
		Sum sum = new Sum();
		return sum.evaluate(toDoubleArray(), metricCollection.toDoubleArray(metricCollection.getTimeWeights()));
	}
	
	public double getMax() {
		Max max = new Max();
		return max.evaluate(toDoubleArray());
	}
	
	public double getMin() {
		Min min = new Min();
		return min.evaluate(toDoubleArray());
	}
	
	public double getMedian() {
		Median median = new Median();
		return median.evaluate(toDoubleArray());
	}
	
	
	
}
