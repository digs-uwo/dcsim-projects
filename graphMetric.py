import sys
import csv
import matplotlib.pyplot as plt

times = []
values = []

traceReader = csv.reader(open(sys.argv[1], 'rb'))

i = 0
currentTime = 0
currentValue = 0
for row in traceReader:	
	if row[1] == sys.argv[2]:
		if i > 0:
			times.append(currentTime)
			values.append(currentValue)
			currentValue= 0
			i = 0
		else:
			i = i + 1
			currentTime = int(row[0])
			currentValue = currentValue + float(row[2])

plt.plot(times, values)
plt.suptitle(sys.argv[2])
plt.show()
