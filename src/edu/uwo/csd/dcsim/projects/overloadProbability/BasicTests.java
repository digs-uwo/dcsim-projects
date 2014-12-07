package edu.uwo.csd.dcsim.projects.overloadProbability;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class BasicTests {

	public static final String[] TRACES = {"traces/clarknet", 
		"traces/epa",
		"traces/sdsc",
		"traces/google_cores_job_type_0", 
		"traces/google_cores_job_type_1",
		"traces/google_cores_job_type_2",
		"traces/google_cores_job_type_3"};	
	
	public static final double UPPER_T = 0.8;
	public static final double LOWER_T = 0.4;
	public static final double TARGET_T = 0.75;
	
	public static final double UPPER_P = 0.9;
	public static final double P_T = 0.2; //probability threshold
	public static final double PREDICT_T = 0.6;
	
	public static final double SLA_CPU_T = 0.5;
	
	public static final int HOST_MAX_VMS = 10;
	
	public static long migCount = 0;
	public static long consolMigCount = 0;

	public static void main(String args[]) {

		ArrayList<HostModel> hosts = buildHosts();	
		
		//train
		for (HostModel host : hosts) {
			host.advance();
		}
		
		//test
		for (int i = 0; i < 1000; ++i) {

//			System.out.print(".");
			
			for (HostModel host : hosts) {
				host.advance();
			}
			
//			runDynamicManagementThreshold(hosts);
//			runDynamicManagementProbability(hosts);
			runDynamicManagementPredicted(hosts);
			
			if (i % 20 == 0) {
				runConsol(hosts);
			}

//			for (HostModel host : hosts) {
//				System.out.println("Host#" + host.id + " util=" + host.getUtilization());
//			}
			
		}
		
		System.out.println("relocMigs = " + migCount);
		System.out.println("consolMigs = " + consolMigCount);
		System.out.println("SLA = " + getAvgSla(hosts));
		System.out.println("SLA-time = " + getSlaVTime(hosts));
		
	}
	
	public static void runDynamicManagementThreshold(ArrayList<HostModel> hosts) {
		for (HostModel host : hosts) {
			if (host.getUtilization() >= UPPER_T) {
				relocate(host, hosts);
			}
		}
	}
	
	public static void runDynamicManagementProbability(ArrayList<HostModel> hosts) {
		for (HostModel host : hosts) {
			if (host.calculateOverloadProbability(UPPER_P) >= P_T) {
				relocate(host, hosts);
			}
		}
	}
	
	public static void runDynamicManagementPredicted(ArrayList<HostModel> hosts) {
		for (HostModel host : hosts) {
			double cpuP = host.predictCpuUsage() / host.cpuSize;
			if (cpuP >= PREDICT_T) {
				System.out.println("CPU=" + host.getUtilization());
				System.out.println("PREDICT=" + cpuP + ">=" + PREDICT_T);
				System.out.println("PROB=" + host.calculateOverloadProbability(UPPER_P) + ">=" + P_T);
				System.out.println("THRESH=" + host.getUtilization() + ">=" + UPPER_T);
				relocate(host, hosts);
				System.out.println("");
			}
		}
	}
	
	public static void runConsol(ArrayList<HostModel> hosts) {
		for (HostModel host : hosts) {
			if (host.getUtilization() < LOWER_T) {
				consolidate(host, hosts);
			}
		}
	}
	
	public static void relocate(HostModel source, ArrayList<HostModel> hosts) {
		boolean found = false;

		VmModel selectedVm = null;
		HostModel selectedTarget = null;

		ArrayList<HostModel> targets = sortTargets(hosts);
		ArrayList<VmModel> vms = new ArrayList<VmModel>();
		vms.addAll(source.vms);
		Collections.sort(vms, new VmComparator());
		Collections.reverse(vms);
		
		for (VmModel vm : vms) {
			for (HostModel target : targets) {
				if (target != source && target.vms.size() < HOST_MAX_VMS &&
						(target.getCpuUse() + vm.getCpuUse()) / (double)target.cpuSize <= TARGET_T) {
					selectedVm = vm;
					selectedTarget = target;
					found = true;
					++migCount;
				}
			}
			if (found) break;
		}	
		
		if (found) migrate(source, selectedTarget, selectedVm, true);
	}
	
	public static void consolidate(HostModel source, ArrayList<HostModel> hosts) {
		ArrayList<HostModel> targets = sortTargets(hosts);
		
		ArrayList<VmModel> vms = new ArrayList<VmModel>();
		vms.addAll(source.vms);
		
		for (VmModel vm : vms) {
			for (HostModel target : targets) {
				if (target != source && target.vms.size() < HOST_MAX_VMS &&
						(target.getCpuUse() + vm.getCpuUse()) / (double)target.cpuSize <= TARGET_T) {
					
					migrate(source, target, vm, false);
					++consolMigCount;
					break;
				}
			}
		}
	}
	
	public static ArrayList<HostModel> sortTargets(ArrayList<HostModel> targets) {
		ArrayList<HostModel> stressed = new ArrayList<HostModel>();
		ArrayList<HostModel> partial = new ArrayList<HostModel>();
		ArrayList<HostModel> under = new ArrayList<HostModel>();
		ArrayList<HostModel> empty = new ArrayList<HostModel>();
		ArrayList<HostModel> sorted = new ArrayList<HostModel>();
		
		//categorize hosts
		for (HostModel host : targets) {
			if (host.getUtilization() >= UPPER_T) {
				stressed.add(host);
			} else if (host.getUtilization() >= LOWER_T) {
				partial.add(host);
			} else if (host.getUtilization() > 0) {
				under.add(host);
			} else {
				empty.add(host);
			}
		}
		
		Collections.sort(partial, new HostComparator());
		Collections.sort(under, new HostComparator());
		Collections.reverse(under);
		
		sorted.addAll(partial);
		sorted.addAll(under);
		sorted.addAll(empty);
		
		return sorted;
	}
	
	public static void migrate(HostModel source, HostModel target, VmModel vm, boolean print) {
		source.vms.remove(vm);
		target.vms.add(vm);
		if (print) System.out.println("Migrated VM#" + vm.id + " from Host#" + source.id + " to Host#" + target.id);
	}
	
	public static double getAvgSla(ArrayList<HostModel> hosts) {
		double sla = 0;
		for (HostModel host : hosts) {
			sla += host.getSlaA();
		}
		return sla / hosts.size();
	}
	
	public static long getSlaVTime(ArrayList<HostModel> hosts) {
		long t = 0;
		for (HostModel host : hosts) {
			t += host.slaVSteps;
		}
		return t;
	}
	
	public static class HostModel {
		long cpuSize;
		int id;
		
		long simSteps;
		long slaVSteps;
		
		ArrayList<VmModel> vms = new ArrayList<VmModel>();
		double overloadP = 0;
		
		public HostModel(int id, long cpuSize) {
			this.id = id;
			this.cpuSize = cpuSize;
		}
		
		public double getSlaA() {
			return (simSteps - slaVSteps) / (double)simSteps;
		}
		
		public double predictCpuUsage() {
			double cpu = 0;
			
			for (VmModel vm : vms) {
				cpu += (vm.getState().utilization * vm.getState().getSelfProbability()) * vm.cpuSize;
				cpu += (vm.getUpperState().utilization * vm.getState().getUpperProbability()) * vm.cpuSize;
				cpu += (vm.getLowerState().utilization * vm.getState().getLowerProbability()) * vm.cpuSize;
			}
			
			return cpu;
		}
		
		public double calculateOverloadProbability(double threshold) {
			overloadP = 0;
			if (vms.size() > 0)	calculateOverloadProbability(vms, new ArrayList<Integer>(), threshold);
			return overloadP;
		}
		
		private void calculateOverloadProbability(ArrayList<VmModel> vms, ArrayList<Integer> states, double threshold) {
			
			//iterate through all possible states of first VM
			VmModel vm = vms.get(0);
			for (int state = 0; state < vm.states.size(); ++state) {
				
				if (Math.abs(state - vm.state) > 1) continue;
				
				//create a new list of states
				ArrayList<Integer> newStates = new ArrayList<Integer>();
				newStates.addAll(states);
				
				newStates.add(state);
				
				//find all states of the other VMs
				if (vms.size() > 1) {
					ArrayList<VmModel> remainingVms = new ArrayList<VmModel>();
					remainingVms.addAll(vms.subList(1, vms.size()));
					
					calculateOverloadProbability(remainingVms, newStates, threshold);
				} else {
					if (calculateStateUtilization(newStates) > threshold) {
						overloadP += calculateStateProbability(newStates);
					
//						printStates(newStates);
//						System.out.println("Util=" + calculateStateUtilization(newStates));
//						System.out.println("P=" + calculateStateProbability(newStates));
					}
				}
				
			}
		}
		
		private double calculateStateUtilization(ArrayList<Integer> states) {
			double util = 0;
			
			for (int i = 0; i < states.size(); ++i) {
				util += vms.get(i).cpuSize * (states.get(i) / (double)10);
			}
			
			return util / this.cpuSize;
		}
		
		private double calculateStateProbability(ArrayList<Integer> states) {
			
			double p = 1;
			for (int i = 0; i < states.size(); ++i) {
				VmModel vm = vms.get(i);
				double transitionP;
				if (states.get(i) == vm.state) {
					transitionP = vm.getState().getSelfProbability();
				} else if (states.get(i) > vm.state && states.get(i) - vm.state == 1) {
					transitionP = vm.getState().getUpperProbability();
				} else if (states.get(i) < vm.state && states.get(i) - vm.state == -1){
					transitionP = vm.getState().getLowerProbability();
				} else {
					//impossible to jump 2 states, return probability of 0
					return 0;
				}
				
				p = p * transitionP;
			}
			return p;
		}
		
		public void printStates(ArrayList<Integer> states) {
			//print states
			for (int i = 0; i < states.size(); ++i) {
				if (i != 0) System.out.print(", ");
				System.out.print(states.get(i));
			}
			System.out.println("");
		}
		
		public double getUtilization() {
			double util = 0;
			for (VmModel vm : vms) {
				util += vm.cpuSize * vm.getUtilization();
			}
			
			return util / cpuSize;
		}
		
		public long getCpuUse() {
			return (long)(getUtilization() * cpuSize);
		}
		
		public void addVm(VmModel vm) {
			vms.add(vm);
		}
		
		public void advance() {
			for (VmModel vm : vms) {
				vm.advance();
			}
			
			simSteps++;
			if (getUtilization() >= SLA_CPU_T) {
				slaVSteps++;
			}
		}
		
		public void printModel() {
			int i = 0;
			for (VmModel vm : vms) {
				System.out.println("VM #" + i);
				vm.printModel();
				++i;
			}
		}
		
	}

	public static class VmModel {
		
		ArrayList<UtilizationState> states = UtilizationState.generateUtilStates();
		WorkloadTrace trace;
		
		int id;
		long cpuSize;
		int state;
		int tracePosition;
		
		public VmModel(String fileName, long cpuSize, int offset, int id) {
			trace = new WorkloadTrace(fileName);
			tracePosition = -1;
			this.cpuSize = cpuSize;
			this.id = id;
		}
				
		public UtilizationState getState() {
			return states.get(state);
		}
		
		public UtilizationState getUpperState() {
			if (state < 10) return states.get(state + 1);
			return states.get(state);
		}
		
		public UtilizationState getLowerState() {
			if (state > 0) return states.get(state - 1);
			return states.get(state);
		}
		
		public void advance() {
			++tracePosition;
			double util = trace.getValue(tracePosition);
			
			int prevState = state;
			state = (int)Math.floor(util * 10);
						
			if (state == prevState) {
				states.get(prevState).selfTransitions++;
			} else if (state > prevState) {
				states.get(prevState).upperTransitions++;
			} else {
				states.get(prevState).lowerTransitions++;
			}
			
		}
		
		public double getUtilization() {
			return trace.getValue(tracePosition);
		}
		
		public long getCpuUse() {
			return (long)(getUtilization() * cpuSize);
		}
		
		public void printModel() {
			for (int i = 0; i <=10; ++i) {
				System.out.println("State " + i + " - upper=" + states.get(i).getUpperProbability() + ", lower=" + states.get(i).getLowerProbability() + ", self=" + states.get(i).getSelfProbability() + " - " +
						states.get(i).getTotalTransitions());
			}
		}
		
		
	}
	
	public static class UtilizationState {
		double utilization;
		long upperTransitions = 1;
		long lowerTransitions = 0;
		long selfTransitions = 0;
		
		public UtilizationState(double utilization) {
			this.utilization = utilization;
		}
		
		public double getUpperProbability() {
			if (getTotalTransitions() == 0) return 0;
			
			return upperTransitions / (double)getTotalTransitions();
		}
		
		public double getLowerProbability() {
			if (getTotalTransitions() == 0) return 0;
			
			return lowerTransitions / (double)getTotalTransitions();
		}
		
		public double getSelfProbability() {
			if (getTotalTransitions() == 0) return 0;
			
			return selfTransitions / (double)getTotalTransitions();
		}
		
		public long getTotalTransitions() {
			return upperTransitions + lowerTransitions + selfTransitions;
		}
		
		public static ArrayList<UtilizationState> generateUtilStates() {
			ArrayList<UtilizationState> states = new ArrayList<UtilizationState>();
			
			for (int i = 0; i <= 10; ++i) {
				states.add(new UtilizationState(0.1 * i));
			}
			
			return states;
		}
		
	}
	
	
	public static class WorkloadTrace {
		private ArrayList<Long> times;
		private ArrayList<Double> values;
		private Long stepSize;
		
		public WorkloadTrace(String fileName) {
			times = new ArrayList<Long>();
			values = new ArrayList<Double>();
			
			try {
				BufferedReader input = new BufferedReader(new FileReader(fileName));
	
				String line;
				
				//read first line, which should contain the step size
				line = input.readLine();
				stepSize = Long.parseLong(line) * 1000; //file is in seconds, simulation runs in ms
				
				int seperator;
				while ((line = input.readLine()) != null) {
					seperator = line.indexOf(',');
					times.add(Long.parseLong(line.substring(0, seperator).trim()) * 1000); //file is in seconds, simulation runs in ms
					values.add(Double.parseDouble(line.substring(seperator + 1).trim()));
				}
				
				input.close();
				
				//if 0 not first, assume (0, 0) as initial time/workload pair
				if (times.get(0) != 0) {
					times.add(0, 0l);
					values.add(0, 0.0);
				}
		
			} catch (FileNotFoundException e) {
				throw new RuntimeException("Could not find trace file '" + fileName + "'", e);
			} catch (IOException e) {
				throw new RuntimeException("Could not load trace file '" + fileName + "'", e);
			}	
			
		}
				
		public ArrayList<Long> getTimes() {
			return times;
		}
		
		public long getLastTime() {
			return times.get(times.size() -1 );
		}
		
		public ArrayList<Double> getValues() {
			return values;
		}
		
		public double getValue(int position) {
			position = position % values.size();
			return values.get(position);
		}
		
		public long getStepSize() {
			return stepSize;
		}
	}
	
	public static class HostComparator implements Comparator<HostModel> {

		@Override
		public int compare(HostModel arg0, HostModel arg1) {
			return Double.compare(arg0.getUtilization(), arg1.getUtilization());
		}
		
	}
	
	public static class VmComparator implements Comparator<VmModel> {

		@Override
		public int compare(VmModel o1, VmModel o2) {
			return Double.compare(o1.getUtilization(), o2.getUtilization());
		}
		
	}
	
	public static ArrayList<HostModel> buildHosts() {
		ArrayList<HostModel> hosts = new ArrayList<HostModel>();
		
		HostModel host;
		
		//build a set of hosts and VMs
		host = new HostModel(1, 2000);
		host.addVm(new VmModel(TRACES[0], 500, 0, 1));
		host.addVm(new VmModel(TRACES[1], 500, 0, 2));
		host.addVm(new VmModel(TRACES[2], 500, 0, 3));
		host.addVm(new VmModel(TRACES[0], 500, 5000, 4));
		host.addVm(new VmModel(TRACES[1], 500, 5000, 5));
		host.addVm(new VmModel(TRACES[2], 500, 500, 6));
		hosts.add(host);
		
		host = new HostModel(2, 2000);
		host.addVm(new VmModel(TRACES[0], 500, 1000, 1));
		host.addVm(new VmModel(TRACES[1], 500, 1000, 2));
		host.addVm(new VmModel(TRACES[1], 500, 500, 3));
		hosts.add(host);
		
		host = new HostModel(3, 2000);
		host.addVm(new VmModel(TRACES[2], 500, 500, 1));
		host.addVm(new VmModel(TRACES[1], 500, 8700, 2));
		host.addVm(new VmModel(TRACES[2], 500, 6512, 3));
		host.addVm(new VmModel(TRACES[0], 500, 1278, 4));
		host.addVm(new VmModel(TRACES[1], 500, 10478, 5));
		hosts.add(host);
		
		host = new HostModel(4, 2000);
		host.addVm(new VmModel(TRACES[0], 500, 267, 1));
		host.addVm(new VmModel(TRACES[1], 500, 9657, 2));
		host.addVm(new VmModel(TRACES[2], 500, 124, 3));
		host.addVm(new VmModel(TRACES[0], 500, 347, 4));
		host.addVm(new VmModel(TRACES[1], 500, 5697, 5));
		host.addVm(new VmModel(TRACES[2], 500, 9874, 6));
		hosts.add(host);
		
		return hosts;
	}
	
}