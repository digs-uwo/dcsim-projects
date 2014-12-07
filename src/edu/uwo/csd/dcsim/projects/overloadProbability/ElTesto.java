package edu.uwo.csd.dcsim.projects.overloadProbability;

import java.util.Random;


public class ElTesto {

	public static void main(String args[]) {
		int states[] = new int[10];

		Random rand = new Random();
		
		for (int i = 0; i < 10; ++i) {
			states[i] = rand.nextInt(5) + 1; 
		}
		
		for (int i = 0; i < 10; ++i) {
			System.out.print(states[i]);
		}
		System.out.println("");
		
		System.out.println(encode(states));
		
	}
	
	public static String encode(int states[]) {
		char[] code = new char[states.length];
		
		for (int i = 0; i < states.length; ++i) {
			code[i] = (char)(states[i] + 97); //48 for numbers, but this allows more states while printable for debugging
			//code += states[states.length - i - 1] * (int)Math.pow(10, i);
		}
		
		return new String(code);
	}
	
}
