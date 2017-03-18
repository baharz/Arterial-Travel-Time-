package utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import eu.amidst.core.distribution.Multinomial;
import eu.amidst.core.distribution.Normal_MultinomialParents;
import eu.amidst.core.variables.Variable;
import eu.amidst.dynamic.inference.InferenceEngineForDBN;
import eu.amidst.dynamic.io.DynamicBayesianNetworkLoader;
import eu.amidst.dynamic.models.DynamicBayesianNetwork;

import java.util.Calendar;
import java.util.HashSet;

public class DataAnalyzer {
	String inPath;
	String outPath;
	double latMin, latMax, lonMin, lonMax;
	Date minDate, maxDate;
	int nDays,minTime,maxTime,nHours;
	public ArrayList<Integer> nCarsPerDay;
	public ArrayList<Integer> nPointPerDay;
	public HashSet<String> allCars;
	final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS'Z'");
	public DataAnalyzer(String inPath, String outPath,double latMin, double latMax, double lonMin,double lonMax, String min_date, String max_date, int min_time, int max_time)
	{
		this.outPath = outPath;
		this.inPath = inPath;
		this.latMin = latMin;
		this.latMax = latMax;
		this.lonMin = lonMin;
		this.lonMax = lonMax;
		this.minTime = min_time;
		this.maxTime = max_time;
		try{
			this.minDate = dateFormat.parse(min_date);
			this.maxDate = dateFormat.parse(max_date);
		}
		catch(Exception e){
			System.out.println("Wrong date format!");
		}
		this.nDays = 0;
		this.nHours = max_time-min_time;
		Calendar c = Calendar.getInstance();
		c.setTime(this.minDate);
		while(c.getTime().before(this.maxDate)){
			c.add(Calendar.DATE, 1);
			this.nDays++;
		}
		System.out.format("Analyzing %d days....", this.nDays);
		nCarsPerDay = new ArrayList<Integer>();
		nPointPerDay = new ArrayList<Integer>();
		allCars = new HashSet<String>();
		
		
		
	}
	
	public void analyze() throws FileNotFoundException, IOException, ParseException
	{
		//e1d70066019e3110f1a74f8d3dc9c657,1,2015-06-01T13:56:04.000Z,39.1566,-76.7794,,
		ArrayList<HashSet<String>> carsPerDay = new ArrayList<HashSet<String>>();
		ArrayList<HashSet<String>> pointPerDay = new ArrayList<HashSet<String>>();
		for(int i=0;i<this.nDays;i++){
			carsPerDay.add(new HashSet<String>());
			pointPerDay.add(new HashSet<String>());
		}
		
		
		System.out.println("Analyzing the samples");
		FileInputStream fis = new FileInputStream(this.inPath);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		BufferedWriter fos = new BufferedWriter(new FileWriter(this.outPath));

		// Read number of edges
		String cur_line = br.readLine();
		int counter = 0;
		while((cur_line=br.readLine()) != null)
		{

			String[] str_splits = cur_line.split(",");
			// Check Lat constraint
			double curLat = Double.parseDouble(str_splits[3]);
			if(curLat>latMax || curLat<latMin)
				continue;
			// Check Lon constraint
			double curLon = Double.parseDouble(str_splits[4]);
			if(curLon>lonMax || curLon<lonMin)
				continue;
			
			// Analyze number of cars per day
			Calendar minCal = Calendar.getInstance();	
			minCal.setTime(this.minDate);
			Calendar maxCal = Calendar.getInstance();
			maxCal.setTime(this.minDate);
			maxCal.set(Calendar.HOUR_OF_DAY, this.maxTime);
			Date curDate = dateFormat.parse(str_splits[2]);
			for(int i=0;i<this.nDays;i++){
				if(curDate.after(minCal.getTime()) && curDate.before(maxCal.getTime())){
					carsPerDay.get(i).add(str_splits[0]);
					pointPerDay.get(i).add(str_splits[0]+"_"+str_splits[1]);
					allCars.add(str_splits[0]);
				}
				maxCal.add(Calendar.DATE, 1);
				minCal.add(Calendar.DATE, 1);
				
			}
			
			
			// The sample meets all the constraints
			counter++;
			System.out.println(counter);
			fos.append(cur_line+"\r\n");
		}
		for(int i=0;i<this.nDays;i++)
			this.nCarsPerDay.add(carsPerDay.get(i).size());
		for(int i=0;i<this.nDays;i++)
			this.nPointPerDay.add(pointPerDay.get(i).size());
		System.out.println("Done!");
		//System.out.println(counter);
		br.close();
		fos.close();
		
	}
	public static void showProbDist(String netPath,String varName){
		DynamicBayesianNetwork DBN;
		try{
		 DBN = DynamicBayesianNetworkLoader.loadFromFile(netPath);
		}
		catch(Exception e){
			System.out.println(e);
			return;
		}
		Variable curVariable = DBN.getDynamicVariables().getVariableByName(varName);
		Variable curHidden = DBN.getDynamicVariables().getVariableByName(varName+"h");
		Normal_MultinomialParents conditional_dists = DBN.getConditionalDistributionTimeT(curVariable);
		//Multinomial curDist = InferenceEngineForDBN.getFilteredPosterior(curHidden);
		//System.out.format("Hidden state distribution for variable %s:",varName+"h");
		//System.out.println(curDist.getProbabilities());
		double mean0 = conditional_dists.getNormal(0).getMean();
		double mean1 = conditional_dists.getNormal(1).getMean();
		System.out.format("mean0: %f, mean1: %f",mean0,mean1);
		
		
		
		
	}
	public static void main(String[] args)
	{
		showProbDist("LearntDBN_SVB_pmpeak_all_from0_tue.dbn","W13");
		
		String[] inPaths = new String[6];
		inPaths[0] = "/media/mahyar/data/java_workspace/data/TripRecordsReport-tripRecords_20151221164705789_MD_June2015/TripRecordsReportWaypoints.csv";
		inPaths[1] = "/media/mahyar/data/java_workspace/data/TripRecordsReport-tripRecords_20151222002134730_MD_July2015/TripRecordsReportWaypoints.csv";
		inPaths[2] = "/media/mahyar/data/java_workspace/data/TripRecordsReport-tripRecords_MD_Feb_01_15_20151217054713916/TripRecordsReportWaypoints.csv";
		inPaths[3] = "/media/mahyar/data/java_workspace/data/TripRecordsReport-tripRecords_MD_Feb_15_28_20151216230349479/TripRecordsReportWaypoints.csv";
		inPaths[4] = "/media/mahyar/data/java_workspace/data/TripRecordsReport-tripRecords_Oct_1_15_20151218000554208/TripRecordsReportWaypoints.csv";
		inPaths[5] = "/media/mahyar/data/java_workspace/data/TripRecordsReport-tripRecords_Oct_15_31_20151218015018099/TripRecordsReportWaypoints.csv";
		
		
		String[] months = {"06","07","02","02","10","10"}; 
		
		String min_time="T16:00:00.000Z";
		String max_time="T18:00:00.000Z";
		
		double latMin = 39.284;
		double latMax = 39.298;
		double lonMin = -76.624;
		double lonMax = -76.595;
		
		
		int[][] selected_days = {{1,30},{1,31},{1,15},{15,28},{1,15},{15,31}};
		
		
		
		for(int i=5;i<6;i++)
		{
			String inPath = inPaths[i];
			String minDate = "2015-" + months[i] + "-" +String.format("%02d", selected_days[i][0]) + min_time;
			String maxDate = "2015-" + months[i] + "-" +String.format("%02d", selected_days[i][1]) + max_time;
			
			DataAnalyzer da = new DataAnalyzer(inPath,"test",latMin,latMax,lonMin,lonMax,minDate,maxDate,16,18);
			try{
				da.analyze();
			}
			catch(Exception e)
			{
				System.out.println(e);
			}
			System.out.println("n trips per day:");
			System.out.print("[");
			for(int j=0;j<da.nCarsPerDay.size();j++){
				System.out.print(da.nCarsPerDay.get(j));
				System.out.print(", ");
			}
			System.out.print("]\r\n");
			
			System.out.println("n GPS points day:");
			System.out.print("[");
			for(int j=0;j<da.nPointPerDay.size();j++){
				System.out.print(da.nPointPerDay.get(j));
				System.out.print(", ");
			}
			System.out.print("]\r\n");
			
			System.out.println("n all cars:");
			System.out.println(da.allCars.size());
				
			
		}
		
		
		//String[] min_dates = {"2015-06-01T16:00:00.000Z","2015-06-08T16:00:00.000Z","2015-06-15T16:00:00.000Z","2015-06-22T16:00:00.000Z"};
		//String[] max_dates = {"2015-06-01T18:00:00.000Z","2015-06-08T18:00:00.000Z","2015-06-15T18:00:00.000Z","2015-06-22T18:00:00.000Z"};
		//String[] min_dates = {"2015-07-06T16:00:00.000Z","2015-07-13T16:00:00.000Z","2015-07-20T16:00:00.000Z","2015-07-27T16:00:00.000Z"};
		//String[] max_dates = {"2015-07-06T18:00:00.000Z","2015-07-13T18:00:00.000Z","2015-07-20T18:00:00.000Z","2015-07-27T18:00:00.000Z"};
//		String[] min_dates = {"2015-02-02T16:00:00.000Z","2015-02-09T16:00:00.000Z"};
//		String[] max_dates = {"2015-02-02T18:00:00.000Z","2015-02-09T18:00:00.000Z"};
		
		//String[] min_dates = {"2015-02-23T16:00:00.000Z"};
		//String[] max_dates = {"2015-02-23T18:00:00.000Z"};
		
//		String[] min_dates = {"2015-10-05T16:00:00.000Z"};
//		String[] max_dates = {"2015-10-05T18:00:00.000Z"};
//		String[] min_dates = {"2015-10-19T16:00:00.000Z","2015-10-26T16:00:00.000Z"};
//		String[] max_dates = {"2015-10-19T18:00:00.000Z","2015-10-26T18:00:00.000Z"};
//		String[] min_dates = {"2015-06-29T16:00:00.000Z"};
//		String[] max_dates = {"2015-06-29T18:00:00.000Z"};
//		DataSelector ds = new DataSelector(inPath,outPath,latMin,latMax,lonMin,lonMax,min_dates,max_dates);
//		try{
//			ds.select();
//		}
//		catch(Exception e)
//		{
//			System.out.println(e);
//		}
//	}
	}

}
