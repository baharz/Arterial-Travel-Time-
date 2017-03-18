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

public class DataSelector {
	String inPath;
	String outPath;
	double latMin, latMax, lonMin, lonMax;
	Date[] minDates, maxDates;
	final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS'Z'");
	public DataSelector(String inPath, String outPath,double latMin, double latMax, double lonMin,double lonMax, String[] min_dates, String[] max_dates)
	{
		this.outPath = outPath;
		this.inPath = inPath;
		this.latMin = latMin;
		this.latMax = latMax;
		this.lonMin = lonMin;
		this.lonMax = lonMax;
		this.minDates = new Date[min_dates.length];
		this.maxDates = new Date[max_dates.length];
		
		if(min_dates.length!=max_dates.length){
			System.out.println("Min dates and max dates should have the same length");
		}
		try{
			for(int i=0;i<min_dates.length;i++)
			{
				minDates[i] = dateFormat.parse(min_dates[i]);
				maxDates[i] = dateFormat.parse(max_dates[i]);
			}
		}
		catch(Exception e)
		{
			System.out.println("Wrong date format!");
		}
		
	}

	
	public void select() throws FileNotFoundException, IOException, ParseException
	{
		System.out.println("Selecting the samples");
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
			
			// Parse the date
			Date curDate = dateFormat.parse(str_splits[2]);
			boolean badDate = true;
			for(int i=0;i<minDates.length;i++)
			{
				if(curDate.after(minDates[i]) && curDate.before(maxDates[i]))
				{
					badDate = false;
					break;
				}
			}
			if(badDate)
				continue;
			
			// The sample meets all the constraints
			counter++;
			System.out.println(counter);
			fos.append(cur_line+"\r\n");
		}
		System.out.println("Done!");
		//System.out.println(counter);
		br.close();
		fos.close();
		
	}
	
	public static void main(String[] args)
	{

		String[] inPaths = new String[6];
		inPaths[0] = "/media/mahyar/data/java_workspace/data/TripRecordsReport-tripRecords_20151221164705789_MD_June2015/TripRecordsReportWaypoints.csv";
		inPaths[1] = "/media/mahyar/data/java_workspace/data/TripRecordsReport-tripRecords_20151222002134730_MD_July2015/TripRecordsReportWaypoints.csv";
		inPaths[2] = "/media/mahyar/data/java_workspace/data/TripRecordsReport-tripRecords_MD_Feb_01_15_20151217054713916/TripRecordsReportWaypoints.csv";
		inPaths[3] = "/media/mahyar/data/java_workspace/data/TripRecordsReport-tripRecords_MD_Feb_15_28_20151216230349479/TripRecordsReportWaypoints.csv";
		inPaths[4] = "/media/mahyar/data/java_workspace/data/TripRecordsReport-tripRecords_Oct_1_15_20151218000554208/TripRecordsReportWaypoints.csv";
		inPaths[5] = "/media/mahyar/data/java_workspace/data/TripRecordsReport-tripRecords_Oct_15_31_20151218015018099/TripRecordsReportWaypoints.csv";
		
		
		String[] outPaths = new String[7];
		outPaths[0] = "selected_june_pmpeak_train";
		outPaths[1] = "selected_july_pmpeak_train";
		outPaths[2] = "selected_feb_pmpeak_01_15_train";
		outPaths[3] = "selected_feb_pmpeak_15_28_train";
		outPaths[4] = "selected_oct_pmpeak_1_15_train";
		outPaths[5] = "selected_oct_pmpeak_15_31_train";
		outPaths[6] = "selected_oct_test";
		
		String[] months = {"06","07","02","02","10","10"}; 
		
		String min_time="T16:00:00.000Z";
		String max_time="T18:00:00.000Z";
		
		double latMin = 39.284;
		double latMax = 39.298;
		double lonMin = -76.624;
		double lonMax = -76.595;
		
		
		int[][] selected_days = {{4,11,18,25},{2,9,16,23,30},{5,12},{19,26},{1,8,15},{22},{29}};
		String output_postfix = "thursday";
		
		for(int i=0;i<outPaths.length;i++)
		{
			String inPath = inPaths[Math.min(i, inPaths.length-1)];
			String outPath = "data/" + outPaths[i] + "_"+ output_postfix + ".csv";
			String[] min_dates = new String[selected_days[i].length];
			String[] max_dates = new String[selected_days[i].length];
			for(int j=0;j<selected_days[i].length;j++)
			{
				min_dates[j] = "2015-" + months[Math.min(i, months.length-1)] + "-" + String.format("%02d", selected_days[i][j])+ min_time;
				max_dates[j] = "2015-" + months[Math.min(i, months.length-1)] + "-" + String.format("%02d", selected_days[i][j])+ max_time;
			}
			DataSelector ds = new DataSelector(inPath,outPath,latMin,latMax,lonMin,lonMax,min_dates,max_dates);
			try{
				ds.select();
			}
			catch(Exception e)
			{
				System.out.println(e);
			}
			
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
