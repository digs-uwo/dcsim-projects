package edu.uwo.csd.dcsim.strategyTree;

import edu.uwo.csd.dcsim.*;

public class AndNode extends Node{

	public AndNode(String name, DataCentre dc, Node[] ch, int evalInterval, int lvl){
		super(name, dc, ch, evalInterval, lvl);
	}
	
	public void evaluate(){
		//System.out.println("Evaluating: " + name);
		
		double childRes = 0;
		
		for (Node child : children){
			childRes += child.getResult();
		}
		results[iteration] = childRes;
		
		iteration++;
	}
	
	public double getAvgMeasurement(){
		return 0;
	}
}
