package com.graphhopper.matching;

public class TowerEntrieNodesId {
	private double nodelat;
	private double nodelon;
	private long nodetime;
	private int intersection;
	private long osmnodeid;
	private int GHnodeid;
	private int entry;
	private int GHEdgeId;
	
	
	// 0= no information, 1= non intersection, 2= stop sign, 
	public void TowerEntrieNodesId(double nodelat,double nodelon,long nodetime, int intersection){
		this.nodelat =nodelat;
		this.nodelon =nodelon;
		this.nodetime =nodetime;
		this.intersection =intersection;
			
		}
	public void TowerEntrieNodesId(){
	}
	
	public void setLat (double nodelat){
		this.nodelat =nodelat;
	}
	public void setLon (double nodelon){
		this.nodelon =nodelon;
	}
	public void setTime (long nodetime){
		this.nodetime =nodetime;
	}
	public void setIntersection (int intersection){
		this.intersection =intersection;
	}
	public void setOSMNode(long osmid){
		this.osmnodeid=osmid;
	}
	public void setGHNode(int GHid){
		this.GHnodeid=GHid;
	}
	public void setEntry(int entry){
		this.entry=entry;
	}
	public void setGHEdgeId(int GHEdgeId) {
		this.GHEdgeId=GHEdgeId;
	}
	public int getGHEdgeId() {
		return GHEdgeId;
	}
	public int getEntry (){
		return entry;
	}
	public double getLat (){
		return nodelat;
	}
	public double getLon (){
		return nodelon;
	}
	public long getTime (){
		return nodetime;
	}
	public int getIntersection (){
		return intersection;
	}
	public long getOSMId(){
		return osmnodeid;
	}
	public int getGHId(){
		return GHnodeid;
	}
	
	public String toString(){
		return getGHId()+","+ getOSMId()+","+getLat ()+","+getLon ()+","+getTime () +","+getIntersection ()+","+getEntry ();
	}

}
