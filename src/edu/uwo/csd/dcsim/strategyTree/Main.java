package edu.uwo.csd.dcsim.strategyTree;

public class Main {

	public static void main(String[] args) {
		/*Tree specification where:
			array length = tree height
			array element size = tree width per level
			in each level:
				0 = directive
				1 = OR Node
				2 = AND Node
		*/
		int[] treeSpecification = {0,1,2,2,0,0,0,0};
		StratTree tree = new StratTree(treeSpecification);
		
		for(int i=1; i<=512; i++){
			tree.run();
		}
	}

}
