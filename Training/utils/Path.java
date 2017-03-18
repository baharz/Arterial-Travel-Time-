package utils;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

public class Path {
	private HashMap<Integer,List<Observation>> ways;
	private int maxStep;
	private int size;
	private double totalTime;
	
	// Constructor
	public Path(){
		this.ways = new HashMap<Integer,List<Observation>>();
		maxStep = 0;
		size=0;
		totalTime = 0;
	}
	
	//Member Functions
	public void addObservation(Observation obs)
	{
		int cur_time= obs.getTime();
		if(cur_time>maxStep)
			maxStep = cur_time;
		
		if(!this.ways.containsKey(cur_time))
			this.ways.put(cur_time, new ArrayList<Observation>());
		
		this.ways.get(cur_time).add(obs);
		this.totalTime += obs.getValue();
		size++;
	}
	public int size()
	{
		return size;
	}
	public double getTotalTime()
	{
		return this.totalTime;
	}
	public List<Observation> getObservationsTimeT(int t)
	{
		if(!this.ways.containsKey(t))
			return new ArrayList<Observation>();
		else
			return this.ways.get(t);
	}
	
	public List<Observation> getPath()
	{
		List<Observation> path = new ArrayList<Observation>();
		// Traverse the hashmap ordered by time
		for(int t=0;t<=maxStep;t++)
		{
			if(!this.ways.containsKey(t))
				continue;
			
			for(Observation obs:this.ways.get(t))
				path.add(obs);
			
		}
		return path;
	}
	
}
