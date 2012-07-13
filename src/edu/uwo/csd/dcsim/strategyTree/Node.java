package edu.uwo.csd.dcsim.strategyTree;

import edu.uwo.csd.dcsim.*;

public abstract class Node {

	private DataCentre dc;
	protected double results[];
	protected Node[] children;
	protected int interval;
	protected int iteration;
	protected int level;
	protected boolean active;
	protected String name;
	
	public Node(String name, DataCentre dc, Node[] ch, int evalInterval, int lvl){
		this.name = name;
		this.dc = dc;
		children = new Node[ch.length];
		for(int i=0; i<ch.length; i++){
			children[i] = ch[i];
		}
		interval = evalInterval;
		results = new double[interval];
		iteration = 0;
		level = lvl;
	}
	
	abstract void evaluate();
	
	public double getResult(){
		iteration = 0;
		double result = 0;
		for(int i=0; i<interval; i++){
			result += results[i];
		}
		return (result >= 0) ? 1.0 : -1.0;
	}
	public int getInterval(){
		return interval;
	}
	public int getLevel(){
		return level;
	}
	public int numChildren(){
		return children.length;
	}
	public void resetIteration(){
		iteration = 0;
	}
	public boolean isActive(){
		return active;
	}
	public void setActive(boolean flag){
		if(flag){
			active=true;
			for(Node child : children){
				child.setActive(true);
			}
		}else{
			active=false;
			for(Node child : children){
				child.setActive(false);
			}
		}
	}
	
	public String getName(){
		return name;
	}
	
	abstract double getAvgMeasurement();
}
