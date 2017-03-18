package utils;

public class Observation {
	private int id;
	private double value;
	private int time;
	
	public Observation(int id,double value,int time)
	{
		this.id = id;
		this.value= value;
		this.time = time;
	}
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public double getValue() {
		return value;
	}
	public void setValue(double value) {
		this.value = value;
	}
	public int getTime() {
		return time;
	}
	public void setTime(int time) {
		this.time = time;
	}
	

}
