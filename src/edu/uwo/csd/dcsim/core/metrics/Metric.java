package edu.uwo.csd.dcsim.core.metrics;

import java.util.ArrayList;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.apache.commons.math3.stat.descriptive.rank.Max;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.stat.descriptive.rank.Min;
import org.apache.commons.math3.stat.descriptive.summary.Sum;

public class Metric {

	private ArrayList<Double> values = new ArrayList<Double>();
	private double tempValue = 0;
	
	public void add(double val) {
		values.add(val);
	}
	
	public double getTempValue() {
		return tempValue;
	}
	
	public void setTempValue(double tempValue) {
		this.tempValue = tempValue;
	}
	
	public ArrayList<Double> getValues() {
		return values;
	}
	
	public double[] valuesArray() {
		double[] array = new double[values.size()];
		
		for (int i = 0; i < values.size(); ++i) {
			array[i] = values.get(i);
		}
		
		return array;
	}
	
	public double getMean() {
		if (values.size() > 0) {
			Mean mean = new Mean();
			return mean.evaluate(valuesArray());
		}
		return 0;
	}
	
	public double getVariance() {
		if (values.size() > 0) {
			Variance variance = new Variance();
			return variance.evaluate(valuesArray());
		}
		return 0;
	}
	
	public double getSum() {
		if (values.size() > 0) {
			Sum sum = new Sum();
			return sum.evaluate(valuesArray());
		}
		return 0;
	}
	
	public double getMax() {
		if (values.size() > 0) {
			Max max = new Max();
			return max.evaluate(valuesArray());
		}
		return 0;
	}
	
	public double getMin() {
		if (values.size() > 0) {
			Min min = new Min();
			return min.evaluate(valuesArray());
		}
		return 0;
	}
	
	public double getMedian() {
		if (values.size() > 0) {
			Median median = new Median();
			return median.evaluate(valuesArray());
		}
		return 0;
	}
	
	
	
}
