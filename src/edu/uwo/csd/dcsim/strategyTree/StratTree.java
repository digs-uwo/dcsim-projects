package edu.uwo.csd.dcsim.strategyTree;

import edu.uwo.csd.dcsim.*;
import edu.uwo.csd.dcsim.management.*;
import edu.uwo.csd.dcsim.host.*;
import java.util.ArrayList;
import java.io.*;

public class StratTree {
	private Node[] tree;
	private DataCentre dc;
	private int execCount = 0;
	private int execOffset = 5;
	File outFile;
	FileWriter out;
	
	public StratTree(int[] specs){
	}
	
	public StratTree(DataCentre dc, VMRelocationPolicy strat1, VMRelocationPolicy strat2, int activeStrat, double a, double b, double c, double d){
		this.dc = dc;
		
		if(activeStrat == 0)
			strat1.startDelayed(120000);
		else
			strat2.startDelayed(120000);
		
		/*
		tree = new Node[7];
		tree[3] = new DirNode("GreenDirNodeSla", dc, 2, 0, "slaViolation", "<", slaThreshold);
		tree[4] = new DirNode("GreenDirNodePow", dc, 2, 0, "power", ">", 9000);
		tree[5] = new DirNode("GreedyDirNodeSla", dc, 2, 0, "slaViolation", ">", slaThreshold);
		tree[6] = new DirNode("GreedyDirNodePow", dc, 2, 0, "power", "<", 10200);
		
		Node[] list = new Node[2];
		list[0] = tree[3];
		list[1] = tree[4];
		tree[1] = new AndNode("GreenAndNode", dc, list, 2, 1);
		
		list = new Node[2];
		list[0] = tree[5];
		list[1] = tree[6];
		tree[2] = new AndNode("GreedyAndNode", dc, list, 2, 1);
		
		list = new Node[2];
		list[0] = tree[1];
		list[1] = tree[2];
		VMRelocationPolicy[] strats = {strat1, strat2};
		tree[0] = new OrNode("OrNode",dc, list, 2, 2, strats, 1);
		
		tree[0].setActive(true);
		for(int i=1; i<tree.length; i++){
			tree[i].setActive(false);
		}
		
		if(activeStrat == 0){
			tree[1].setActive(true);
			tree[3].setActive(true);
			tree[4].setActive(true);
		}else{
			tree[2].setActive(true);
			tree[5].setActive(true);
			tree[6].setActive(true);
		}
		*/
		
		
		//***************************************************************************
		/*
		tree = new Node[3];
		tree[2] = new DirNode("GreenDirNode", dc, 2, 0, "powerEfficiency", ">", 1.3);
		tree[1] = new DirNode("GreedyDirNode", dc, 2, 0, "powerEfficiency", "<", 1.3);
		
		Node[] list = new Node[2];
		list[0] = tree[2];
		list[1] = tree[1];
		VMRelocationPolicy[] strats = {strat1, strat2};
		tree[0] = new OrNode("OrNode", dc, list, 2, 1, strats, 0);
		
		tree[0].setActive(true);
		if(activeStrat == 0){
			tree[2].setActive(true);
			tree[1].setActive(false);
		}else{
			tree[2].setActive(false);
			tree[1].setActive(true);
		}*/
		
		tree = new Node[7];
		
		System.out.println("Thresholds in StratTree Constructor: " + a + ", " + b + ", " + c + ", " + d);
		
		//greedy DIR nodes
		tree[6] = new DirNode("GreedyPowerNode", dc, execOffset, 0, "powerEfficiency", "<", d);
		tree[5] = new DirNode("GreedySLANode", dc, execOffset, 0, "slaViolation", ">", c);
		
		//green DIR nodes
		tree[4] = new DirNode("GreenPowerNode", dc, execOffset, 0, "powerEfficiency", ">", b);
		tree[3] = new DirNode("GreenSLANode", dc, execOffset, 0, "slaViolation", "<", a);
		
		//AND nodes
		Node[] children = new Node[2];
		children[0] = tree[5];
		children[1] = tree[6];
		tree[2] = new AndNode("GreedyAndNode", dc, children, 1, 1);
		children = new Node[2];
		children[0] = tree[3];
		children[1] = tree[4];
		tree[1] = new AndNode("GreenAndNode", dc, children, 1, 1);
		
		//OR Nodes
		children = new Node[2];
		children[0] = tree[1];
		children[1] = tree[2];
		VMRelocationPolicy[] strats = {strat1, strat2};
		tree[0] = new OrNode("OrNode", dc, children, 1, 2, strats, 0);
		
		tree[0].setActive(true);
		if(activeStrat == 0){
			tree[1].setActive(true);
			tree[2].setActive(false);
		}else{
			tree[1].setActive(false);
			tree[2].setActive(true);
		}

		try{
			outFile = new File("workload.csv");
			out = new FileWriter(outFile);
		}catch(Exception e){}
	}
	
	public void run(){
		try{
			double workload = 0;
			ArrayList<Host> hosts = dc.getHosts();
			for(Host host : hosts){
				workload += host.getCpuManager().getCpuInUse();
			}
			
			out.write(workload + "\n");
		}catch(Exception e){}
		
		execCount++;
		//System.out.println("StratTree run: " + execCount);
		for(int i=tree.length-1; i>=0; i--){
			//condition for each node to run: if node not in bottom level, execCount % offset must be 0
			if((tree[i].getLevel() == 0) || (execCount % execOffset == 0)){
				if(tree[i].isActive()){
					tree[i].evaluate();
				}
			}
		}
	}
}
