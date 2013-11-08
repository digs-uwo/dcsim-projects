package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.Comparator;

/**
 * Compares ClusterData objects by a (non-empty) series of attributes or factors. The 
 * available factors are:
 * 
 * + ACTIVE_RACKS:				Number of active Racks in the Cluster;
 * + MAX_SPARE_CAPACITY:		Amount of spare resources available in the least loaded active Host in the Cluster;
 * + MIN_INACTIVE_HOSTS:		Number of inactive Hosts in the Rack with more active Hosts;
 * + POWER_CONSUMPTION:			Sum of power consumption from all Racks and Switches in the Cluster;
 * + POWER_EFFICIENCY: 			Total CPU units over power consumption when the Cluster is fully utilized.
 * 
 * @author Gaston Keller
 *
 */
public enum ClusterDataComparator implements Comparator<ClusterData> {
	
	ACTIVE_RACKS {
		public int compare(ClusterData o1, ClusterData o2) {
			double compare = o1.getCurrentStatus().getActiveRacks() - o2.getCurrentStatus().getActiveRacks();
			if (compare < 0)
				return -1;
			else if (compare > 0)
				return 1;
			return 0;
		}
	},
	MAX_SPARE_CAPACITY {
		public int compare(ClusterData o1, ClusterData o2) {
			double compare = o1.getCurrentStatus().getMaxSpareCapacity() - o2.getCurrentStatus().getMaxSpareCapacity();
			if (compare < 0)
				return -1;
			else if (compare > 0)
				return 1;
			return 0;
		}
	},
	MIN_INACTIVE_HOSTS {
		public int compare(ClusterData o1, ClusterData o2) {
			double compare = o1.getCurrentStatus().getMinInactiveHosts() - o2.getCurrentStatus().getMinInactiveHosts();
			if (compare < 0)
				return -1;
			else if (compare > 0)
				return 1;
			return 0;
		}
	},
	POWER_CONSUMPTION {
		public int compare(ClusterData o1, ClusterData o2) {
			double compare = o1.getCurrentStatus().getPowerConsumption() - o2.getCurrentStatus().getPowerConsumption();
			if (compare < 0)
				return -1;
			else if (compare > 0)
				return 1;
			return 0;
		}
	},
	POWER_EFFICIENCY {
		public int compare(ClusterData o1, ClusterData o2) {
			double compare = o1.getClusterDescription().getPowerEfficiency() - o2.getClusterDescription().getPowerEfficiency();
			if (compare < 0)
				return -1;
			else if (compare > 0)
				return 1;
			return 0;
		}
	};
	
	public static Comparator<ClusterData> getComparator(final ClusterDataComparator... multipleOptions) {
        return new Comparator<ClusterData>() {
            public int compare(ClusterData o1, ClusterData o2) {
                for (ClusterDataComparator option : multipleOptions) {
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
