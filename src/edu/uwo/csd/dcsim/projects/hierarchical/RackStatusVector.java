package edu.uwo.csd.dcsim.projects.hierarchical;

import java.util.Arrays;

import edu.uwo.csd.dcsim.host.Resources;

/**
 * @author Gaston Keller
 *
 */
public class RackStatusVector {

	public final Resources[] vmVector = {VmFlavours.xtiny(), VmFlavours.tiny(), VmFlavours.small(), VmFlavours.medium(), VmFlavours.large(), VmFlavours.xlarge()};
	public int[] vector = new int[3 + vmVector.length];	// spare capacity vector: [active, suspended, poweredOff] + vmVector
	public final int iActive = 0;
	public final int iSuspended = 1;
	public final int iPoweredOff = 2;
	public final int iVmVector = 3;
	
	public RackStatusVector() {
		// Do nothing.
	}
	
	public RackStatusVector(RackStatusVector source) {
		for (int i = 0; i < vector.length; i++) {
			vector[i] = source.vector[i];
		}
	}
	
	public RackStatusVector copy() {
		return new RackStatusVector(this);
	}
	
	@Override
	public String toString() {
		return Arrays.toString(vector);
	}

}
