package com.graphhopper.matching;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.jgrapht.alg.ConnectivityInspector;
import java.io.Writer;

import org.jgrapht.ext.MatrixExporter;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.coll.LongIntMap;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GPXEntry;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PathMerger;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.Translation;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.shapes.BBox;
import com.sun.corba.se.impl.javax.rmi.CORBA.Util;
import eu.amidst.dynamic.learning.parametric.bayesian.semiPath;


public class MapMatchingResult {
	public GraphHopperStorage graph;
	public MyGraphHopper hopper;

	public MatchResult mr;
	HashMap<Long,Integer> thisOSMtoGH=new HashMap<Long,Integer>();
	HashMap<Integer,Long> thisGHtoOSM= new HashMap<Integer,Long>();
	HashMap<Long, ReaderWay>  thisWaysTags= new HashMap<Long, ReaderWay>(); 
	List <Double []> entry_nodes = new ArrayList <Double []>();

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private static final BitUtil bitUtil = BitUtil.LITTLE;
	String finalstr = null;
	int id=0;
	public ArrayList<ArrayList <semiPath>> allpaths() throws IOException{
		// Writing the headers first
		ArrayList<ArrayList <semiPath>> allthesemipathes= new ArrayList<ArrayList <semiPath>>();
		try(Stream<java.nio.file.Path> paths = Files.walk(Paths.get("junejulyfeboct"))) {
			paths.forEach(filePath -> {
				if (Files.isRegularFile(filePath)) {
					//System.out.println(filePath);
					String []args= new String [4];
					args [0]= "action=match";
					args [1]= "gpx="+filePath;
					args [2]="gps_accuracy=40";
					args [3]="max_visited_nodes=3000";
					start(CmdArgs.read(args));
					List <TowerEntrieNodesId> nodestest= getInfo(mr, hopper,graph);
					ArrayList <semiPath> semipathsList= listofsemiPath(nodestest);
					allthesemipathes.add(semipathsList);
				}
			});
		}
		try{
			File file = new File("allsemipaths");
			FileOutputStream f = new FileOutputStream(file);
			ObjectOutputStream s = new ObjectOutputStream(f);
			s.writeObject(allthesemipathes);
			System.out.println("allthesemipathesdone");
		}   
		catch(Exception ex){
			ex.printStackTrace();
		}
		return allthesemipathes;
	}
	
	//making Array list from Array list of semi-paths
	public ArrayList <semiPath> listofsemiPath(List <TowerEntrieNodesId> onepathnodes){
		ArrayList <semiPath> onecarSemiPaths= new ArrayList<semiPath>();
		for (int i=0;i<onepathnodes.size();i++)
			if (onepathnodes.get(i).getTime()>0){
				semiPath curSemiPath= new semiPath();
				curSemiPath.setStartTime(onepathnodes.get(i).getTime());
				for (int j=i+1;j<onepathnodes.size();j++){
					if (onepathnodes.get(j).getTime()>0){
						curSemiPath.setEndTime(onepathnodes.get(j).getTime());
						for (int k=i;k<j;k++){
							curSemiPath.semipathNodes.add(onepathnodes.get(k).getGHEdgeId());
						}
						break;
					}
				}
				onecarSemiPaths.add(curSemiPath);
			}
		return onecarSemiPaths;
	}


	//making prior t for the graph 
	public void makingPrior(GraphHopperStorage graph) throws IOException{
		EdgeIterator iter= graph.getAllEdges();
		BufferedWriter writer =new BufferedWriter(new FileWriter("priors.txt")) ;
		FlagEncoder myencoder = graph.getEncodingManager().getEncoder("car");
		List<double[]> priors = new ArrayList<double[]>();
		while (iter.next())
		{
			int nodeA = iter.getBaseNode();
			int nodeB = iter.getAdjNode();
			int edge= iter.getEdge();
			System.out.println(edge);
			long flag= iter.getFlags();
			double speed=myencoder.getSpeed(flag);
			double length= graph.getEdgeIteratorState(edge,nodeB).getDistance();
			double priortt= (length/speed)*3600;
			double variance= Math.pow(((length/(0.8*speed))*3600-priortt),2);
			double [] linkPrior= new double [3];
			linkPrior[0]= edge;
			linkPrior[1]= priortt;
			linkPrior[2]= variance;
			priors.add(linkPrior);
			priors.add(edge, linkPrior);
			writer.append(edge+","+priortt+","+variance +"\r\n");
			writer.flush();
		}
		try{
			File file = new File("priorsjava");
			FileOutputStream f = new FileOutputStream(file);
			ObjectOutputStream s = new ObjectOutputStream(f);
			s.writeObject(priors);
		}   
		catch(Exception ex){
			ex.printStackTrace();
		}
		//writer.close();
	}
	//prior as the mean of all the observed
	public void printArray(double arr[])
	{
		System.out.print("Array: [");
		for(int i = 0; i<arr.length;i++){
			if(i<arr.length-1)
				System.out.print(arr[i]+",");
			else
				System.out.println(arr[i]+"]");
			
		}
		
	}
	public double [][] priorsAsMean() throws IOException{
		List<double[]> priors = new ArrayList<double[]>();
		try {
			FileInputStream f = new FileInputStream("priorsjava");
			ObjectInputStream s = new ObjectInputStream(f);
			priors = (List<double[]>)s.readObject();
		
			
			s.close();
			
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.out.println("priorsjava");
		}	
		
	int nedge = 0;

	File folder = new File("junejulyfeboct");
	File[] files = folder.listFiles();
	for(int i=0;i<files.length;i++)
	{
		if(files[i].isFile()){
			
			String []args= new String [4];
			args [0]= "action=match";
			args [1]= "gpx="+files[i];
			args [2]="gps_accuracy=40";
			args [3]="max_visited_nodes=3000";
			start(CmdArgs.read(args));
			nedge = graph.getAllEdges().getMaxId();
			break;
		}
	}
	final int n_edges = nedge; 
	double priorsAsMean[][] = new double [n_edges][4];
		try(Stream<java.nio.file.Path> paths = Files.walk(Paths.get("junejulyfeboct"))) {
			
			paths.forEach(filePath -> {
				if (Files.isRegularFile(filePath)) {
					System.out.println(filePath);
					String []args= new String [4];
					args [0]= "action=match";
					args [1]= "gpx="+filePath;
					args [2]="gps_accuracy=40";
					args [3]="max_visited_nodes=3000";
					start(CmdArgs.read(args));
					int id=0;
					//int n_edges = graph.getAllEdges().getMaxId();
					List <TowerEntrieNodesId> nodestest= getInfo(mr, hopper,graph);
					List <TowerEntrieNodesId>nodetime= setTimes(nodestest,graph);
					;
					try {
						double linkTravelTime []= linkTT(nodetime, graph, id);
						printArray(linkTravelTime);
						id=id+1;
						for (int i=0;i<linkTravelTime.length;i++){
							if (linkTravelTime[i]>0){
							priorsAsMean[i][3]=priorsAsMean[i][3]+1;
							}
							priorsAsMean[i][1]=priorsAsMean[i][1]+ linkTravelTime[i];
							priorsAsMean[i][2]=Math.pow((priorsAsMean[i][1]- linkTravelTime[i]),2);
							
						}
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();

					}
				}
			});
			for (int i=0;i<n_edges;i++){
				priorsAsMean[i][0]=i;
				if (priorsAsMean[i][3]>1){
					priorsAsMean[i][1]=priorsAsMean[i][1]/priorsAsMean[i][3];
				}
				if (priorsAsMean[i][3]>1){
					priorsAsMean[i][2]=priorsAsMean[i][2]/(priorsAsMean[i][3]-1);	
				}
				printArray(priorsAsMean[i]);
			}
			for (int i=0;i<n_edges;i++){
				priorsAsMean[i][0]=i;
				if (priorsAsMean[i][1]==0){
					priorsAsMean[i][1]= priors.get(i)[1];
					priorsAsMean[i][2]= priors.get(i)[2];
				}
				printArray(priorsAsMean[i]);	
			}		
		}
		catch(Exception e){
			
		}
		try{
			File file = new File("priorsAsMean");
			FileOutputStream f = new FileOutputStream(file);
			ObjectOutputStream s = new ObjectOutputStream(f);
			s.writeObject(priorsAsMean);
		}   
		catch(Exception ex){
			ex.printStackTrace();
		}
		return priorsAsMean;
	}
	//Setting the time for each lat and lon
	public void allmatches() throws IOException{
		BufferedWriter writer = new BufferedWriter(new FileWriter("testall.txt"));
		// Writing the headers first

		try(Stream<java.nio.file.Path> paths = Files.walk(Paths.get("testall"))) {

			paths.forEach(filePath -> {
				if (Files.isRegularFile(filePath)) {
					System.out.println(filePath);
					String []args= new String [4];
					args [0]= "action=match";
					args [1]= "gpx="+filePath;
					args [2]="gps_accuracy=40";
					args [3]="max_visited_nodes=3000";
					start(CmdArgs.read(args));
					List <TowerEntrieNodesId> nodestest= getInfo(mr, hopper,graph);
					List <TowerEntrieNodesId>nodetime= setTimes(nodestest,graph);
					try {
						String cur_str= DBNPreparation (nodetime, graph,id );
						writer.append(cur_str+ "\r\n");
						writer.flush();
						id=id+1;

					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});
		} 

		//writer.write(finalstr);
		writer.close();
	}
	public double []linkTT (List <TowerEntrieNodesId> important_nodes_times,GraphHopperStorage graph, int carid) throws Exception{

		int dayofweek;
		double cumulitive_relativespeed=0;
		int n = important_nodes_times.size();
		EdgeIteratorState x;
		FlagEncoder myencoder = graph.getEncodingManager().getEncoder("car");
		int car_id = carid;
		int n_time_bins = 24;
		int n_edges = graph.getAllEdges().getMaxId();
		
		double []linktime = new double[n_edges];
		double[][] seen_edges = new double[n_time_bins][n_edges];
		for(double[] row:seen_edges){
			java.util.Arrays.fill(row, 0);
		}
		Date datei= new Date(important_nodes_times.get(0).getTime());
		int d= datei.getDate();
		int y= datei.getYear();
		int m= datei.getMonth();
		int h=16 ;
		int mn=0;
		int s= 0;
		Date compareto = new Date();
		compareto.setYear(y);
		compareto.setMonth(m);
		compareto.setDate(d);
		compareto.setHours(h);
		compareto.setMinutes(mn);
		compareto.setSeconds(s);
		long millis = compareto.getTime();
		for(int i=0;i<n-1;i++){
			x=graph.getEdgeIteratorState(important_nodes_times.get(i).getGHEdgeId(),important_nodes_times.get(i+1).getGHId());
			double dist=x.getDistance();
			long flag=x.getFlags();
			double speed=myencoder.getSpeed(flag); 
			double deltat= important_nodes_times.get(i+1).getTime()-important_nodes_times.get(i).getTime();
			double link_speed =(3600000/1000)*( dist/deltat);
			double relativespeed= link_speed/speed;
			cumulitive_relativespeed =(relativespeed+i*cumulitive_relativespeed)/(i+1);
			double timediff = (important_nodes_times.get(i).getTime()-millis)/(5*60*1000);
			int tmestep= (int)Math.floor(timediff);
			int cur_edge_id = important_nodes_times.get(i).getGHEdgeId();
			seen_edges[tmestep][cur_edge_id] = deltat;
			linktime [cur_edge_id]= deltat;
		}
		
		return linktime;
	}
	public String DBNPreparation (List <TowerEntrieNodesId> important_nodes_times,GraphHopperStorage graph, int carid) throws Exception{

		List <TowerEntrieNodesId> DBNPreparedList = new ArrayList <TowerEntrieNodesId>();

		int dayofweek;
		double cumulitive_relativespeed=0;
		int n = important_nodes_times.size();
		EdgeIteratorState x;
		FlagEncoder myencoder = graph.getEncodingManager().getEncoder("car");
		int car_id = carid;
		int n_time_bins = 24;
		int n_edges = graph.getAllEdges().getMaxId();
		
		double linksTravelTime[][] = new double [n_edges][4];
		double[][] seen_edges = new double[n_time_bins][n_edges];
		for(double[] row:seen_edges){
			java.util.Arrays.fill(row, -1);
		}
		Date datei= new Date(important_nodes_times.get(0).getTime());
		int d= datei.getDate();
		int y= datei.getYear();
		int m= datei.getMonth();
		int h= 16;
		int mn= 0;
		int s= 0;
		Date compareto = new Date();
		compareto.setYear(y);
		compareto.setMonth(m);
		compareto.setDate(d);
		compareto.setHours(h);
		compareto.setMinutes(mn);
		compareto.setSeconds(s);
		long millis = compareto.getTime();
		for(int i=0;i<n-1;i++){
			x=graph.getEdgeIteratorState(important_nodes_times.get(i).getGHEdgeId(),important_nodes_times.get(i+1).getGHId());
			double dist=x.getDistance();
			long flag=x.getFlags();
			double speed=myencoder.getSpeed(flag); 
			double deltat= important_nodes_times.get(i+1).getTime()-important_nodes_times.get(i).getTime();
			double link_speed =(3600000/1000)*( dist/deltat);
			double relativespeed= link_speed/speed;
			cumulitive_relativespeed =(relativespeed+i*cumulitive_relativespeed)/(i+1);
			//			String fourpm = "06-01-2015 16:00:00";
			//			SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy hh:mm:ss");
			//			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS'Z'");
			//			Date targetdate = sdf.parse(fourpm);
			//			long millisec = targetdate.getTime();
			//			double mytime = (important_nodes_times.get(i).getTime()-millisec)/(5*60*1000);
			double timediff = (important_nodes_times.get(i).getTime()-millis)/(5*60*1000);
			int tmestep= (int)Math.floor(timediff);
			//			Calendar mycalendar=Calendar.getInstance();
			//			mycalendar.setTime(datei);
			//			final int dom= mycalendar.DAY_OF_MONTH;
			//			final int year=mycalendar.YEAR;
			//			final int month= mycalendar.MONTH;
			//			int hour= 16;
			//			int minute=0;
			//			mycalendar.set(year, month,dom,hour,minute);
			//			long millis= mycalendar.getTimeInMillis();



			System.out.println(flag+","+speed+","+relativespeed+","+cumulitive_relativespeed+","+tmestep+","+deltat);
			int cur_edge_id = important_nodes_times.get(i).getGHEdgeId();
			seen_edges[tmestep][cur_edge_id] = deltat;
			
		}
		String car_str = "";
		for(int i=0;i<n_time_bins;i++)
		{
			int first_non_zero=-1;
			for(int j=0;j<n_edges;j++)
			{
				if(seen_edges[i][j]>0)
				{
					first_non_zero = j;
					break;
				}
			}
			if(first_non_zero==-1)
				continue;
			String cur_step = "";
			cur_step+= String.valueOf(car_id)+','+String.valueOf(i);
			for(int j=0;j<n_edges;j++)
			{
				cur_step+=',';
				double cur_duration = seen_edges[i][j];
				if(cur_duration==-1)
					cur_step+='?';
				else
					cur_step+=String.valueOf(cur_duration);
				
			}
			car_str += cur_step+ "\r\n";
		}

		//		BufferedWriter writer = new BufferedWriter(new FileWriter("test.txt"));
		//		writer.write(car_str);
		//		writer.close();
		return car_str;

	}

	public List <TowerEntrieNodesId> setTimes(List <TowerEntrieNodesId> important_nodes,GraphHopperStorage graph )
	{
		List <TowerEntrieNodesId> important_nodes_times = new ArrayList <TowerEntrieNodesId>();
		//		DistanceCalcEarth distance = new DistanceCalcEarth();

		int n = important_nodes.size();
		boolean first=true;
		EdgeIteratorState x;
		FlagEncoder myencoder = graph.getEncodingManager().getEncoder("car");
		for(int i=0;i<n-1;i++){


			if(first && important_nodes.get(i).getEntry()==0){
				important_nodes_times.add(important_nodes.get(i));

				continue;
			}		
			first=false;
			if(important_nodes.get(i).getEntry()== 1){
				important_nodes_times.add(important_nodes.get(i));
			}

			else{
				//initiate nxt_previous_dist and next_previous_time
				double nxt_previous_dist=graph.getEdgeIteratorState(important_nodes.get(i-1).getGHEdgeId(),important_nodes.get(i).getGHId()).getDistance();
				long flagi=graph.getEdgeIteratorState(important_nodes.get(i).getGHEdgeId(),important_nodes.get(i+1).getGHId()).getFlags();
				double next_previou_speed=myencoder.getSpeed(flagi);
				double next_previous_time=3600*(nxt_previous_dist/next_previou_speed);

				double current_previous_dist=0;
				double current_previous_time=0;
				double current_previous_speed=0;
				for(int j=i+1;j<n;j++)
				{

					nxt_previous_dist=graph.getEdgeIteratorState(important_nodes.get(j-1).getGHEdgeId(),important_nodes.get(j).getGHId()).getDistance()+nxt_previous_dist;
					double distj=graph.getEdgeIteratorState(important_nodes.get(j-1).getGHEdgeId(),important_nodes.get(j).getGHId()).getDistance();
					double speedj=myencoder.getSpeed(flagi);
					double timej=3600*(distj/speedj);
					next_previous_time=next_previous_time+timej;

					TowerEntrieNodesId next= important_nodes.get(j);
					TowerEntrieNodesId previous =  important_nodes.get(i-1);
					if(next.getEntry()==1){
						current_previous_dist=graph.getEdgeIteratorState(important_nodes.get(i-1).getGHEdgeId(),important_nodes.get(i).getGHId()).getDistance();
						current_previous_speed=myencoder.getSpeed(flagi);
						current_previous_time= 3600*(current_previous_dist/current_previous_speed);
						//double nxt_previous_dist = distance.calcDist(previous.getLat(), previous.getLon(), next.getLat(), next.getLon());
						//double current_previous_dist = distance.calcDist(previous.getLat(), previous.getLon(), important_nodes.get(i).getLat(), important_nodes.get(i).getLon());
						double fractiontime=Math.abs((current_previous_time/next_previous_time));
						double fraction =  Math.abs((current_previous_dist/nxt_previous_dist));
						double timediff = next.getTime()-previous.getTime();
						important_nodes_times.add(important_nodes.get(i));
						important_nodes_times.get(i).setTime((long)(timediff*fractiontime+previous.getTime()));
						//important_nodes_times.get(i).setTime((long)(timediff*fraction+previous.getTime()));
						break;
					}
				} 
			}
		}

		return important_nodes_times;
	}

	//filling out the TowerEntrieNodesId
	public List<TowerEntrieNodesId> getInfo(MatchResult mr, GraphHopper hopper, GraphHopperStorage graph){

		Boolean[] hasEntry = new Boolean[mr.getEdgeMatches().size()];
		List <TowerEntrieNodesId> important_nodes = new ArrayList <TowerEntrieNodesId>();


		int count =0;
		GPXEntry epmty = new GPXEntry(-1,-1,-1,-1);


		for(EdgeMatch em:mr.getEdgeMatches())
		{
			int GHEdgeId = em.getEdgeState().getEdge();

			TowerEntrieNodesId intersection_entry = new TowerEntrieNodesId();
			intersection_entry.setGHEdgeId(GHEdgeId);
			int GHNode= em.getEdgeState().getBaseNode();
			long OSMNode= thisGHtoOSM.get(new Integer(GHNode));
			double lat = hopper.getGraphHopperStorage().getNodeAccess().getLatitude(GHNode);
			double lon = hopper.getGraphHopperStorage().getNodeAccess().getLongitude(GHNode);
			intersection_entry.setLat(lat);
			intersection_entry.setLon(lon);
			intersection_entry.setGHNode(GHNode);
			intersection_entry.setOSMNode(OSMNode);
			if (thisOSMtoGH.get(new Long (OSMNode))<0){
				intersection_entry.setIntersection(-2);
			}
			else if (thisOSMtoGH.get(new Long (OSMNode))>0){
				intersection_entry.setIntersection(1);
			}
			else{
				intersection_entry.setIntersection(-1);
			}
			important_nodes.add(intersection_entry);



			if(em.getGpxExtensions().size()>0)
			{
				hasEntry[count] =true;
				intersection_entry.setTime(em.getGpxExtensions().get(0).getEntry().getTime());
				intersection_entry.setEntry(1);
				//				double lati=em.getGpxExtensions().get(0).getEntry().getLat();
				//				double loni= em.getGpxExtensions().get(0).getEntry().getLon();
				//				QueryResult qr=hopper.getLocationIndex().findClosest(lati,loni, EdgeFilter.ALL_EDGES);
				//				int l= qr.getClosestNode();
				//				double lt=hopper.getGraphHopperStorage().getNodeAccess().getLat(l);
				//				double ln=hopper.getGraphHopperStorage().getNodeAccess().getLon(l);
				//				Double[] d= new Double [2];
				//				d[0]=lt;d[1]=ln;
				//				entry_nodes.add(d);8

			}
			else{
				intersection_entry.setEntry(0);
				//intersection_entry.setTime(0); 
			}
			count++;    	
		}
		int totalnodes =important_nodes.size();
		return important_nodes;	
	}
	public ArrayList<Integer> getGraphElements(GraphHopperStorage graph){
		EdgeIterator iter= graph.getAllEdges();
		FlagEncoder myencoder = graph.getEncodingManager().getEncoder("car");
		List<Integer> allIntersections= new ArrayList<Integer>();
		ArrayList<Integer> alledges=new ArrayList<Integer>();
		MatrixExporter <Integer,DefaultWeightedEdge> myadjmatrix= new MatrixExporter <Integer,DefaultWeightedEdge>();
		int count=0;
		int all=0;
		int wrong= 0;
		while (iter.next())
		{
			int nodeA = iter.getBaseNode();
			int nodeB = iter.getAdjNode();
			long edgeFlag= iter.getFlags();

			if (!allIntersections.contains(nodeA)){
				allIntersections.add(nodeA);
			}
			if (!allIntersections.contains(nodeB)){
				allIntersections.add(nodeB);
			}		
			if(nodeA==nodeB){
				continue;	
			}
			alledges.add(iter.getEdge());
		}
		return alledges;
	}
	public void Connectivity(GraphHopperStorage graph,MyGraphHopper hopper) throws FileNotFoundException{
		EdgeIterator iter= graph.getAllEdges();
		EdgeIterator iter2= graph.getAllEdges();
		FlagEncoder myencoder = graph.getEncodingManager().getEncoder("car");
		DirectedWeightedMultigraph<Integer,DefaultWeightedEdge>  baharGraph= new DirectedWeightedMultigraph<Integer,DefaultWeightedEdge>(DefaultWeightedEdge.class);
		List<Integer> allIntersections= new ArrayList<Integer>();
		FileOutputStream out = new FileOutputStream("adjmtrx.txt");
		Writer output= new OutputStreamWriter(out);
		MatrixExporter <Integer,DefaultWeightedEdge> myadjmatrix= new MatrixExporter <Integer,DefaultWeightedEdge>();
		int count=0;
		int all=0;
		int wrong= 0;
		int counter = 0;
		int num_Edge = graph.getAllEdges().getMaxId();
		while (iter.next())
		{
			System.out.println(Integer.toString(counter)+"/" + Integer.toString(num_Edge));
			counter++;
			int nodeA = iter.getBaseNode();
			int nodeB = iter.getAdjNode();




			if (!allIntersections.contains(nodeA)){
				allIntersections.add(nodeA);
			}
			if (!allIntersections.contains(nodeB)){
				allIntersections.add(nodeB);
			}		

			if (nodeA==nodeB){wrong++;}
			all=all+2;    
			long edgeFlag= iter.getFlags();
			//thisWaysTags.get(hopper.getOSMWay(iter.getEdge())).;



			//if (!(thisOSMtoGH.get(thisGHtoOSM.get(nodeA))<0)||!(thisOSMtoGH.get(thisGHtoOSM.get(nodeB))<0)){
			//count=count+1;
			//}

			//System.out.println("nointersection = "+count);
			//System.out.println("all = "+all);
			if(nodeA==nodeB){
				continue;
			}
			//from here 
			//while (iter2.next())
			//			{
			//				long edgeFlag2= iter2.getFlags();
			//				if (iter==iter2){
			//					continue; 
			//				}
			//				int nodeA2 = iter2.getBaseNode();
			//				int nodeB2 = iter2.getAdjNode();
			//				
			//				if (nodeA2==nodeA){	
			//					if (myencoder.isBackward(edgeFlag)&&
			//				}
			//				if (nodeB2==nodeB){
			//					
			//				}
			//				if (nodeB2==nodeA){
			//					
			//				}
			//			if (nodeB==nodeA2){
			//					
			//				}
			//			}

			if(!baharGraph.containsVertex(nodeA))
			{
				baharGraph.addVertex(nodeA);
			}
			if(!baharGraph.containsVertex(nodeB))
			{
				baharGraph.addVertex(nodeB);
			}
			System.out.println(edgeFlag);

			if (myencoder.isBackward(edgeFlag))
			{

				DefaultWeightedEdge curWay= new DefaultWeightedEdge();
				baharGraph.addEdge(nodeB,nodeA,curWay);
				//System.out.println("BnodeAlat="+graph.getNodeAccess().getLatitude(nodeA)+","+graph.getNodeAccess().getLongitude(nodeA));
				//System.out.println("BnodeBlat="+graph.getNodeAccess().getLatitude(nodeB)+","+graph.getNodeAccess().getLongitude(nodeB));
				int curWeight = iter.getEdge();
				baharGraph.setEdgeWeight(curWay,curWeight);
			}
			else if (myencoder.isForward(edgeFlag))
			{
				//System.out.println("FnodeAlat="+graph.getNodeAccess().getLatitude(nodeA)+","+graph.getNodeAccess().getLongitude(nodeA));
				//System.out.println("FnodeBlat="+graph.getNodeAccess().getLatitude(nodeB)+","+graph.getNodeAccess().getLongitude(nodeB));
				DefaultWeightedEdge curWay= new DefaultWeightedEdge();
				baharGraph.addEdge(nodeA,nodeB, curWay);
				int curWeight = iter.getEdge();
				baharGraph.setEdgeWeight(curWay,curWeight);

			}

			else{ 
				//System.out.println("WnodeAlat="+graph.getNodeAccess().getLatitude(nodeA)+","+graph.getNodeAccess().getLongitude(nodeA));
				//System.out.println("WnodeBlat="+graph.getNodeAccess().getLatitude(nodeB)+","+graph.getNodeAccess().getLongitude(nodeB));
				DefaultWeightedEdge curWay= new DefaultWeightedEdge();
				baharGraph.addEdge(nodeA,nodeB,curWay);
				DefaultWeightedEdge curWay1= new DefaultWeightedEdge();
				baharGraph.addEdge(nodeB,nodeA,curWay1);

				int curWeight = iter.getEdge();
				baharGraph.setEdgeWeight(curWay,curWeight);
				baharGraph.setEdgeWeight(curWay1,curWeight);
			}

		}
		System.out.println("donebahargraph");
		myadjmatrix.exportAdjacencyMatrix(output, baharGraph);
		//		ConnectivityInspector connectivity= new ConnectivityInspector(baharGraph);
		//		if (connectivity.isGraphConnected()){
		//			System.out.println("yesssssssssssssss");
		//		}
		//		else System.out.println("Noooooooooooo");
		System.out.println("nodeadj");
		Set<DefaultWeightedEdge> alledges= baharGraph.edgeSet();
		boolean[][] matrix = new boolean[graph.getAllEdges().getMaxId()][graph.getAllEdges().getMaxId()];
		for (int curnode:allIntersections){
			Set<DefaultWeightedEdge> incomingWaySet= baharGraph.incomingEdgesOf(curnode);
			Set<DefaultWeightedEdge> outgoingWaySet= baharGraph.outgoingEdgesOf(curnode);
			for (DefaultWeightedEdge tempway:incomingWaySet){
				for (DefaultWeightedEdge tempway2:outgoingWaySet){
					matrix[(int)baharGraph.getEdgeWeight(tempway)][(int)baharGraph.getEdgeWeight(tempway2)]=true;
				}

			}
		}



		try
		{
			PrintWriter pr = new PrintWriter("mtxsmall.txt");   
			pr.println(matrix.length);

			for(int i=0;i<matrix.length;i++)
			{
				for (int j=0;j<matrix.length;j++)
					if (matrix[i][j]){
						pr.println(i+","+j);		   
					}
				pr.flush();
			}
			pr.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.out.println("No such file exists.");
		}
		//		try{
		//			File file = new File("adjmatrix");
		//			FileOutputStream f = new FileOutputStream(file);
		//			ObjectOutputStream s = new ObjectOutputStream(f);
		//			s.writeObject(matrix);
		//			System.out.println("osmdone");
		//		}   
		//		catch(Exception ex){
		//			ex.printStackTrace();
		//		}

		System.out.println("edgeadj");
	}
	//Importing and making graph hopper and final file
	//The following function borrowed from https://github.com/graphhopper/map-matching
	public void start(CmdArgs args) {
		String action = args.get("action", "").toLowerCase();
		args.put("graph.location", "./graph-cache");
		if (action.equals("import")) {
			String flagEncoders = args.get("vehicle", "").toLowerCase();
			if (flagEncoders.isEmpty()) {
				flagEncoders = args.get("vehicles", "car").toLowerCase();
			}

			args.put("graph.flag_encoders", flagEncoders);
			args.put("datareader.file", args.get("datasource", ""));

			// standard should be to remove disconnected islands            
			if (!args.has("prepare.min_one_way_network_size")) {
				args.put("prepare.min_one_way_network_size", 200);
			}
			logger.info("Configuration: " + args);

			hopper = new MyGraphHopper().init(args);     
			hopper.getCHFactoryDecorator().setEnabled(false);
			hopper.importOrLoad();
			HashMap<Integer,Long> myGHtoOSMMap = hopper.reader.GHtoOSMmapTest;
			HashMap<Long, ReaderNode>  mynodesTags= hopper.reader.nodesTags;
			HashMap<Long, ReaderWay>  mywaysTags= hopper.reader.waysTags;



			try{
				File file = new File ("myGHtoOSMMapFile");
				FileOutputStream f = new FileOutputStream(file);
				ObjectOutputStream s = new ObjectOutputStream(f);
				s.writeObject(myGHtoOSMMap);
				s.close();
				System.out.println("GHdone");
			}   
			catch(Exception ex){
				ex.printStackTrace();
			}
			HashMap<Long,Integer> myOSMtoGHMap =hopper.reader.OSMtoGHmapTest;
			try{
				File file = new File("myOSMtpGHMapFile");
				FileOutputStream f = new FileOutputStream(file);
				ObjectOutputStream s = new ObjectOutputStream(f);
				s.writeObject(myOSMtoGHMap);
				System.out.println("osmdone");
			}   
			catch(Exception ex){
				ex.printStackTrace();
			}

			//			try{
			//				File file = new File ("myWaysRedearFile");
			//				FileOutputStream f = new FileOutputStream(file);
			//				ObjectOutputStream s = new ObjectOutputStream(f);
			//				s.writeObject(mywaysTags);
			//				s.close();
			//				System.out.println("WaysTagsDone");
			//			}   
			//			catch(Exception ex){
			//				ex.printStackTrace();
			//			}

			int a = 10;
		} else if (action.equals("match")) {
			thisOSMtoGH=loadOSMtoGH();
			thisGHtoOSM= loadGHtoOSM();
			//thisWaysTags=loadWaysReader();
			hopper = new MyGraphHopper();
			hopper.init(args);
			hopper.getCHFactoryDecorator().setEnabled(false);
			logger.info("loading graph from cache");
			hopper.load("./graph-cache");
			FlagEncoder firstEncoder = hopper.getEncodingManager().fetchEdgeEncoders().get(0);
			graph = hopper.getGraphHopperStorage();
			graph.getNodes();

			int gpsAccuracy = args.getInt("gps_accuracy", -1);
			if (gpsAccuracy < 0) {
				// backward compatibility since 0.8
				gpsAccuracy = args.getInt("gpx_accuracy", 40);
			}

			String instructions = args.get("instructions", "");
			logger.info("Setup lookup index. Accuracy filter is at " + gpsAccuracy + "m");
			LocationIndexMatch locationIndex = new LocationIndexMatch(graph,
					(LocationIndexTree) hopper.getLocationIndex());
			MapMatching mapMatching = new MapMatching(graph, locationIndex, firstEncoder);
			mapMatching.setMaxVisitedNodes(args.getInt("max_visited_nodes", 1000));
			mapMatching.setTransitionProbabilityBeta(args.getDouble("transition_probability_beta", 0.00959442));
			mapMatching.setMeasurementErrorSigma(gpsAccuracy);

			// do the actual matching, get the GPX entries from a file or via stream
			String gpxLocation = args.get("gpx", "");
			File[] files = getFiles(gpxLocation);

			logger.info("Now processing " + files.length + " files");
			StopWatch importSW = new StopWatch();
			StopWatch matchSW = new StopWatch();

			Translation tr = new TranslationMap().doImport().get(instructions);

			for (File gpxFile : files) {
				try {
					importSW.start();
					List<GPXEntry> inputGPXEntries = new GPXFile().doImport(gpxFile.getAbsolutePath()).getEntries();
					importSW.stop();
					matchSW.start();
					mr = mapMatching.doWork(inputGPXEntries);
					matchSW.stop();
					System.out.println(gpxFile);
					System.out.println("\tmatches:\t" + mr.getEdgeMatches().size() + ", gps entries:" + inputGPXEntries.size());
					System.out.println("\tgpx length:\t" + (float) mr.getGpxEntriesLength() + " vs " + (float) mr.getMatchLength());
					System.out.println("\tgpx time:\t" + mr.getGpxEntriesMillis() / 1000f + " vs " + mr.getMatchMillis() / 1000f);

					String outFile = gpxFile.getAbsolutePath() + ".res.gpx";

					System.out.println("\texport results to:" + outFile);

					InstructionList il;
					if (instructions.isEmpty()) {
						il = new InstructionList(null);
					} else {
						PathWrapper matchGHRsp = new PathWrapper();
						Path path = mapMatching.calcPath(mr);
						//double dist =path.getDistance();
						new PathMerger().doWork(matchGHRsp, Collections.singletonList(path), tr);
						il = matchGHRsp.getInstructions();
					}
					//new GPXFile(mr, il).doExport(outFile);
				} catch (Exception ex) {
					importSW.stop();
					matchSW.stop();
					logger.error("Problem with file " + gpxFile + " Error: " + ex.getMessage(), ex);
				}
			}
			System.out.println("gps import took:" + importSW.getSeconds() + "s, match took: " + matchSW.getSeconds());
		} else if (action.equals("getbounds")) {
			String gpxLocation = args.get("gpx", "");
			File[] files = getFiles(gpxLocation);
			BBox bbox = BBox.createInverse(false);
			for (File gpxFile : files) {
				List<GPXEntry> inputGPXEntries = new GPXFile().doImport(gpxFile.getAbsolutePath()).getEntries();
				for (GPXEntry entry : inputGPXEntries) {
					bbox.update(entry.getLat(), entry.getLon());
				}
			}

			System.out.println("max bounds: " + bbox);

			// show download only for small areas
			if (bbox.maxLat - bbox.minLat < 0.1 && bbox.maxLon - bbox.minLon < 0.1) {
				double delta = 0.01;
				System.out.println("Get small areas via\n"
						+ "wget -O extract.osm 'http://overpass-api.de/api/map?bbox="
						+ (bbox.minLon - delta) + "," + (bbox.minLat - delta) + ","
						+ (bbox.maxLon + delta) + "," + (bbox.maxLat + delta) + "'");
			}
		} else {
			System.out.println("Usage: Do an import once, then do the matching\n"
					+ "./map-matching action=import datasource=your.pbf\n"
					+ "./map-matching action=match gpx=your.gpx\n"
					+ "./map-matching action=match gpx=.*gpx\n\n"
					+ "Or start in-built matching web service\n"
					+ "./map-matching action=start-server\n\n");
		}
	}

	//this is called in start
	public File[] getFiles(String gpxLocation) {
		if (gpxLocation.contains("*")) {
			int lastIndex = gpxLocation.lastIndexOf(File.separator);
			final String pattern;
			File dir = new File(".");
			if (lastIndex >= 0) {
				dir = new File(gpxLocation.substring(0, lastIndex));
				pattern = gpxLocation.substring(lastIndex + 1);
			} else {
				pattern = gpxLocation;
			}

			return dir.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.matches(pattern);
				}
			});
		} else {
			return new File[]{
					new File(gpxLocation)
			};
		}
	}
	public HashMap<Long, Integer> loadOSMtoGH(){
		HashMap<Long,Integer> OSMtoGHReading=new HashMap<Long,Integer>();
		//String address= filename;
		try {
			FileInputStream f = new FileInputStream("myOSMtpGHMapFile");
			ObjectInputStream s = new ObjectInputStream(f);
			OSMtoGHReading = (HashMap<Long,Integer>) s.readObject();
			s.close();
			;
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.out.println("cant load OSMtoGH file");
		}
		return OSMtoGHReading;
	}

	public HashMap<Integer,Long> loadGHtoOSM(){
		HashMap<Integer, Long> GHtoOSMReading= new HashMap<Integer, Long>();
		//String address= filename;
		try {
			FileInputStream f = new FileInputStream("myGHtoOSMMapFile");
			ObjectInputStream s = new ObjectInputStream(f);
			GHtoOSMReading = (HashMap<Integer, Long>)s.readObject();
			s.close();
			;
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.out.println("cant load GHtoOSM file");
		}
		return GHtoOSMReading;
	}
	public HashMap<Long, ReaderWay> loadWaysReader(){
		HashMap<Long, ReaderWay> waysReaderReading= new HashMap <Long,ReaderWay>();
		//String address= filename;
		try {
			FileInputStream f = new FileInputStream("myWaysRedearFile");
			ObjectInputStream s = new ObjectInputStream(f);
			waysReaderReading = (HashMap<Long, ReaderWay>)s.readObject();
			s.close();
			;
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.out.println("cant load Ways Reader file");
		}
		return waysReaderReading;
	}


	// this is just my thoughts!
	public void getLists(MatchResult mr, MyGraphHopper hopper, GraphHopperStorage graph, InstructionList il,String outFile){
		List <Integer> intEdgeIdList= new ArrayList <Integer>() ;
		List <Long> osmwayList= new ArrayList <Long>() ;
		List <GPXEntry> gpxEntryList = new ArrayList <GPXEntry>();
		List<EdgeMatch> matches = mr.getEdgeMatches();
		List<Integer> basenodeList = new ArrayList <Integer>();
		List<GPXEntry> latlonList = new ArrayList<GPXEntry> ();
		for( EdgeMatch match : matches )
		{
			int basenode= match.getEdgeState().getBaseNode();
			basenodeList.add(basenode);

			double lat = hopper.getGraphHopperStorage().getNodeAccess().getLatitude(match.getEdgeState().getBaseNode());
			double lon = hopper.getGraphHopperStorage().getNodeAccess().getLongitude(match.getEdgeState().getBaseNode());
			GPXEntry intersection = new GPXEntry (lat, lon,Double.NaN,0);
			double latadj = hopper.getGraphHopperStorage().getNodeAccess().getLatitude(match.getEdgeState().getAdjNode());
			double lonadj = hopper.getGraphHopperStorage().getNodeAccess().getLongitude(match.getEdgeState().getAdjNode());

			latlonList.add(intersection);

			int internalEdgeId = match.getEdgeState().getEdge();

			if (match.getEdgeState() instanceof VirtualEdgeIteratorState) {
				// first, via and last edges can be virtual
				VirtualEdgeIteratorState vEdge = (VirtualEdgeIteratorState) match.getEdgeState();
				internalEdgeId = vEdge.getOriginalTraversalKey() / 2;
			}
			Long osmway = hopper.getOSMWay(internalEdgeId);

			EdgeIteratorState edge=graph.getEdgeIteratorState(internalEdgeId, Integer.MIN_VALUE);  
			long OSMNodeBase = edge.getBaseNode();
			long OSMNodeAdj = edge.getAdjNode();

			OSMReader  myOSMRedear = hopper.reader;
			LongIntMap OSMNodeMap = myOSMRedear.getNodeMap();
			intEdgeIdList.add(internalEdgeId);
			osmwayList.add(osmway);                   

			for( GPXExtension gpxExt : match.getGpxExtensions() )
			{
				GPXEntry gpx = gpxExt.getEntry();
				gpxEntryList.add(gpx);
			}
		}
		for (int i=0; i<latlonList.size();i++){
			System.out.println(latlonList.get(i));
		}
		for (int i=0; i<basenodeList.size();i++){
			System.out.println(basenodeList.get(i));
		}
		for (int i=0; i<intEdgeIdList.size();i++){
			System.out.println(intEdgeIdList.get(i));
		}

		for (int i=0;i<gpxEntryList.size();i++){
			System.out.println(gpxEntryList.get(i)); 
		}
		for (int i=0; i<osmwayList.size();i++){
			System.out.println(osmwayList.get(i));
		}
		double length = mr.getMatchLength();
		System.out.println(mr.toString());
		System.out.println(getInfo(mr, hopper, graph).toString());
		System.out.println(setTimes(getInfo(mr, hopper,graph), graph).toString());

		new GPXFile(mr, il).doExport(outFile);
		List<GPXEntry> allfinalnodes= new GPXFile(mr, il).getEntries();
		int nn=allfinalnodes.size();
		//List<TowerEntrieNodesId> finallist= getInfo(allfinalnodes,entries, latlonList);
		System.out.println(allfinalnodes.toString());

	}
}
