package edu.uwo.csd.dcsim.projects.applicationManagement;

import java.util.Comparator;

import edu.uwo.csd.dcsim.host.*;

/**
 * Compares racks by a (non-empty) series of attributes or factors. The 
 * available factors are:
 * 
 * + CPU_CORES:		host's total number of cores across all CPUs;
 * + CORE_CAP:		host's core capacity;
 * + MEMORY:		host's memory;
 * + BANDWIDTH:		host's bandwidth;
 * + CPU_UTIL: 		host stub's current CPU utilization;
 * + CPU_IN_USE:	host stub's current CPU in use;
 * + EFFICIENCY:	host's power efficiency at 100% CPU utilization;
 * + PWR_STATE:		host stub's current power state.
 * 
 * @author Gaston Keller
 * @author Michael Tighe modified for Racks
 *
 */
public enum RackComparator implements Comparator<Rack> {
	
	CPU_UTIL {
		public int compare(Rack o1, Rack o2) {
			
			//total rack CPU utilization
			double util1 = 0;
			double total1 = 0;
			double util2 = 0;
			double total2 = 0;
			
			for (Host host : o1.getHosts()) {
				util1 += host.getResourceManager().getCpuInUse();
				total1 += host.getResourceManager().getTotalCpu();
			}
			util1 = util1 / total1;
			
			for (Host host : o2.getHosts()) {
				util2 += host.getResourceManager().getCpuInUse();
				total2 += host.getResourceManager().getTotalCpu();
			}
			util2 = util2 / total2;
			
			
			return (int)(util1 - util2);
		}
	},
	HOSTS_ON {
		public int compare(Rack o1, Rack o2) {
			
			//total powered on hosts
			int on1 = 0;
			int on2 = 0;
			
			for (Host host : o1.getHosts()) {
				if (host.getState() == Host.HostState.ON) on1++;
			}
			
			for (Host host : o2.getHosts()) {
				if (host.getState() == Host.HostState.ON) on2++;			
			}
			
			return on1 - on2;
		}
	};
	
	public static Comparator<Rack> getComparator(final RackComparator... multipleOptions) {
        return new Comparator<Rack>() {
            public int compare(Rack o1, Rack o2) {
                for (RackComparator option : multipleOptions) {
                    int result = option.compare(o1, o2);
                    if (result != 0) {
                        return result;
                    }
                }
                return 0;
            }
        };
    }

}
