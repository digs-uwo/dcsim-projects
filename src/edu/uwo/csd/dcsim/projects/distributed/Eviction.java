package edu.uwo.csd.dcsim.projects.distributed;

import java.util.ArrayList;

import edu.uwo.csd.dcsim.common.HashCodeUtil;
import edu.uwo.csd.dcsim.management.VmStatus;
import edu.uwo.csd.dcsim.projects.distributed.events.RequestResourcesEvent;
import edu.uwo.csd.dcsim.projects.distributed.events.ResourceOfferEvent;

public class Eviction {
	
	private RequestResourcesEvent event;
	private ArrayList<VmStatus> vmList;
	private ArrayList<ResourceOfferEvent> resourceOffers = new ArrayList<ResourceOfferEvent>();

	private static int hashNum = 1;
	private final int hashCode;
	
	public Eviction() {
		//init hashCode
		hashCode = generateHashCode();
	}
	
	public ArrayList<VmStatus> getVmList() {
		return vmList;
	}
	
	public void setVmList(ArrayList<VmStatus> vmList) {
		this.vmList = vmList;
	}
	
	public void setVmList(VmStatus vm) {
		vmList = new ArrayList<VmStatus>();
		vmList.add(vm);
	}
	
	public ArrayList<ResourceOfferEvent> getResourceOffers() {
		return resourceOffers;
	}
	
	public RequestResourcesEvent getEvent() {
		return event;
	}
	
	public void setEvent(RequestResourcesEvent event) {
		this.event = event;
	}
	
	@Override
	public int hashCode() {
		return hashCode;
	}
	
	private int generateHashCode() {
		int result = HashCodeUtil.SEED;
		result = HashCodeUtil.hash(result, hashNum++);
		return result;
	}
	
}
