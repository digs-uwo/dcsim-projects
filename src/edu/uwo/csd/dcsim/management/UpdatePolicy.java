package edu.uwo.csd.dcsim.management;

import edu.uwo.csd.dcsim.DataCentre;
import edu.uwo.csd.dcsim.core.Event;
import edu.uwo.csd.dcsim.core.Simulation;
import edu.uwo.csd.dcsim.strategyTree.StratTree;

public class UpdatePolicy extends ManagementPolicy {
	
	//interval corresponding to finest execution interval of strategy tree
	DataCentre dc;
	long interval;
	VMRelocationPolicy strat1, strat2;
	StratTree tree;

	public UpdatePolicy(Simulation simulation, 
						DataCentre dc, 
						VMRelocationPolicy strat1, 
						VMRelocationPolicy strat2, 
						long interval, 
						int startStrat,
						double a,
						double b,
						double c,
						double d){
		super(simulation);
		this.dc = dc;
		this.interval = interval;
		tree = new StratTree(dc, strat1, strat2, startStrat, a, b, c, d);
	}

	@Override
	public void execute() {
		tree.run();
	}

	@Override
	public long getNextExecutionTime() {
		// TODO Auto-generated method stub
		return simulation.getSimulationTime() + interval;
	}

	@Override
	public void processEvent(Event e) {
		// Nothing to do here?  I'm not sure what this method is for
	}

}
