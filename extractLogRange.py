import sys
import csv

times = []
values = []

traceReader = csv.reader(open(sys.argv[1], 'rb'), delimiter='-')

lower = long(sys.argv[2])
upper = long(sys.argv[3])
for row in traceReader:	
	time = long(row[0])
	if time >= lower and time <= upper:
		print(row[0] + '-' + row[1])
