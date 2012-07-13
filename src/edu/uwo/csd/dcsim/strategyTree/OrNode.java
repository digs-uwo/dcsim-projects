package edu.uwo.csd.dcsim.strategyTree;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.management.*;

public class OrNode extends Node{

	private int activeStrat;
	private VMRelocationPolicy[] strats;
	public OrNode(String name, DataCentre dc, Node[] ch, int evalInterval, int lvl, VMRelocationPolicy[] strats, int activeStrat){
		super(name, dc, ch, evalInterval, lvl);
		this.activeStrat = activeStrat;
		this.strats = strats;
	}
	
	public void evaluate(){
		double result = 0;
		for(int i=0; i<children.length; i++){
			children[i].resetIteration();
		}
		
		for(Node child : children){
			if(child.isActive()){
				result += child.getResult();
			}
		}
		
		if(result < 0){
			System.out.println("SWITCH");
			switchStrat();
		}
	}
	
	public void switchStrat(){
		if(activeStrat == 0){
			activeStrat = 1;
			strats[0].stop();
			//strats[1].start();
			strats[1].startDelayed(120000);
			children[0].setActive(false);
			children[1].setActive(true);
			//System.out.println("Switching to greedy");
		}else{
			activeStrat = 0;
			strats[1].stop();
			//strats[0].start();
			strats[0].startDelayed(120000);
			children[0].setActive(true);
			children[1].setActive(false);
			//System.out.println("Switching to green");
		}
	}
	
	public VMRelocationPolicy getStrategy(){
		return strats[activeStrat];
	}
	
	public double getAvgMeasurement(){
		return 0;
	}
}
