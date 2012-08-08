#! /usr/bin/python

import sys
import csv
import re
import matplotlib.pyplot as plt

def main():
	traceReader = csv.reader(open(sys.argv[1], 'rb'))
	trace = readTrace(traceReader)

	createMainGraph(trace)
	#createMigrationGraph(trace)

#Plots a graph of a number of primary metrics
def createMainGraph(trace):
	#plot DC utilization
	times, values = getValues("avgDcUtil", trace)
	plt.plot(times, values, 'b.', label="dcUtil")

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

#Plots a graph of migrations
def createMigrationGraph(trace):

	#plot sla violation
	times, values = getValues("slaViolation", trace)
	plt.plot(times, scale(values, 500), 'y-', label="slaV")

	#plot relocations
	times, values = getValuesRegex("migrationCount-VMRelocationPolicy*", trace)
	plt.plot(times, values, 'b.', label="relocations")

	#plot consolidations
	times, values = getValuesRegex("migrationCount-VMConsolidationPolicy*", trace)
	plt.plot(times, values, 'y.', label="consolidations")

	#plot strategy switches
	times, values = getValues("stratSlaEnable", trace)
	plt.plot(times, scale(values, 100), 'r', label="enableSla")
	times, values = getValues("stratPowerEnable", trace)
	plt.plot(times, scale(values, 100), 'g', label="enablePower")

	plt.legend()
	plt.suptitle("Migrations")
	plt.show()

def scale(values, scale):
	newValues = []

	for value in values:
		newValues.append(value * scale)

	return newValues

def filterOut(values, valueToFilter):
	newValues = []
	
	for value in values:
		if value != valueToFilter:
			newValues.append(value)

	return newValues

def getValuesRegex(metricRegex, trace):
	times = []
	values = []

	p = re.compile(metricRegex)

	for row in trace:	
		if p.match(row[1]):
			times.append(int(row[0]))
			values.append(float(row[2]))

	return (times, values)

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


