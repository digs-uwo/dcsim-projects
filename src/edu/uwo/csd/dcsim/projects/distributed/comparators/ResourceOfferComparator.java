package edu.uwo.csd.dcsim.projects.distributed.comparators;

import java.util.Comparator;

import edu.uwo.csd.dcsim.projects.distributed.events.ResourceOfferEvent;

/**
 * Compares hosts by a (non-empty) series of attributes or factors. The 
 * available factors are:
 * 
 * + CPU_CORES:		total number of cores across all CPUs;
 * + CORE_CAP:		core capacity;
 * + MEMORY:		memory;
 * + BANDWIDTH:		bandwidth;
 * + CPU_UTIL: 		current CPU utilization;
 * + CPU_IN_USE:	current CPU in use;
 * + EFFICIENCY:	power efficiency of the host at 100% CPU utilization;
 * + PWR_STATE:		current power state.
 * 
 * @author Gaston Keller
 *
 */
public enum ResourceOfferComparator implements Comparator<ResourceOfferEvent> {
	
	CPU_IN_USE {
		public int compare(ResourceOfferEvent o1, ResourceOfferEvent o2) {
			double compare = o1.getHostStatus().getResourcesInUse().getCpu() - o2.getHostStatus().getResourcesInUse().getCpu(); 
			if (compare < 0)
				return -1;
			else if (compare > 0)
				return 1;
			return 0;
		}
	},
	CPU_UTIL {
		public int compare(ResourceOfferEvent o1, ResourceOfferEvent o2) {
			double compare = (o1.getHostStatus().getResourcesInUse().getCpu() / o1.getHost().getTotalCpu()) - 
					(o2.getHostStatus().getResourcesInUse().getCpu() / o2.getHost().getTotalCpu()); 
			if (compare < 0)
				return -1;
			else if (compare > 0)
				return 1;
			return 0;
		}
	},
	EFFICIENCY {
		public int compare(ResourceOfferEvent o1, ResourceOfferEvent o2) {
			double compare = o1.getHost().getPowerEfficiency(1) - o2.getHost().getPowerEfficiency(1);
			if (compare < 0)
				return -1;
			else if (compare > 0)
				return 1;
			return 0;
		}
	};
	
	public static Comparator<ResourceOfferEvent> getComparator(final ResourceOfferComparator... multipleOptions) {
        return new Comparator<ResourceOfferEvent>() {
            public int compare(ResourceOfferEvent o1, ResourceOfferEvent o2) {
                for (ResourceOfferComparator option : multipleOptions) {
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
