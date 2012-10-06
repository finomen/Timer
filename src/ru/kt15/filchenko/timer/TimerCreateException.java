package ru.kt15.filchenko.timer;

public class TimerCreateException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4452402731624023767L;

	public TimerCreateException(Exception e) {
		super(e);
	}
	
	public TimerCreateException(String s) {
		super(s);
	}

}
