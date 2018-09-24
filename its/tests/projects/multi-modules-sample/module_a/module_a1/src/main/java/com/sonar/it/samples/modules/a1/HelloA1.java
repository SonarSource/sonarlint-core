package com.sonar.it.samples.modules.a1;

public class HelloA1 {
	private int i;
	private HelloA1() {
		
	}
	
	public void hello() {
		System.out.println("hello" + " world");
	}
	
	protected String getHello() {
		return "hello";
	}
}