#! /usr/bin/python

import sys
import csv
import matplotlib.pyplot as plt

def main():
	traceReader = csv.reader(open(sys.argv[1], 'rb'))
	trace = readTrace(traceReader)

	#plot DC utilization
	times, values = getValues("avgDcUtil", trace)
	plt.plot(times, values, 'b-', label="dcUtil")

	#plot sla violation
	times, values = getValues("slaViolation", trace)
	plt.plot(times, scale(values, 50), 'y-', label="slaV")

	#plot power efficiency
	times, values = getValues("optimalPowerEfficiencyRatio", trace)
	plt.plot(times, scale(values, 0.5), 'm-', label="power-e")

	#plot strategy switches
	times, values = getValues("stratSlaEnable", trace)
	plt.plot(times, values, 'r', label="enableSla")
	times, values = getValues("stratPowerEnable", trace)

	plt.plot(times, values, 'g', label="enablePower")

	plt.legend()
	plt.suptitle("slaViolation")
	plt.show()

def scale(values, scale):
	newValues = []

	for value in values:
		newValues.append(value * scale)

	return newValues

def filterOut(values, valueToFilter):
	newValues = []
	
	for value in values:
		if value != valueToFiler:
			newValues.append(value)

	return newValues

def getValues(metricName, trace):
	times = []
	values = []

	for row in trace:	
		if row[1] == metricName:
			times.append(int(row[0]))
			values.append(float(row[2]))

	return (times, values)

def readTrace(traceReader):
	trace = []
	for row in traceReader:
		trace.append(row)
	return trace

if __name__ == "__main__":
	main()	


