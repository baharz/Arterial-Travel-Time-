package dbnmaking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import eu.amidst.core.datastream.DataStream;
import eu.amidst.core.distribution.Multinomial;
import eu.amidst.core.distribution.Normal;
import eu.amidst.core.distribution.Normal_MultinomialParents;
import eu.amidst.core.distribution.UnivariateDistribution;
import eu.amidst.core.inference.messagepassing.VMP;
import eu.amidst.dynamic.datastream.DynamicDataInstance;
import eu.amidst.dynamic.inference.DynamicMAPInference;
import eu.amidst.dynamic.inference.DynamicVMP;
import eu.amidst.dynamic.inference.FactoredFrontierForDBN;
import eu.amidst.dynamic.io.DynamicDataStreamLoader;
import eu.amidst.dynamic.models.DynamicBayesianNetwork;
import eu.amidst.dynamic.io.DynamicBayesianNetworkLoader;
import eu.amidst.dynamic.inference.InferenceEngineForDBN;
import eu.amidst.core.variables.Variable;
import utils.Observation;
import utils.Path;
import org.apache.commons.io.FilenameUtils;
public class TravelTimeTest {
	private int nWays = -1;
	private int nTimeSteps;
	private List<Path> carTestPaths;
	private DataStream<DynamicDataInstance> data;
	private String convertedTestFile;
	private DynamicBayesianNetwork DBN, origDBN;
	private FactoredFrontierForDBN inference;
	private double[] priors;
	private boolean weightedAverage;
	private boolean ignore_unseen_samples;
	private boolean[] ignore_link_flags;
	
	public TravelTimeTest(){}
	public TravelTimeTest(String DBN_path,String dataPath, String priorPath, boolean ignore_unseen_samples, String train_data_path)
	{
		// train_data_path is just for determing the links which have not been seen during training
		
		weightedAverage = false;
		// Reading data
		
		// Convert the test data
		//--Create the output file name
		java.nio.file.Path javaDataPath = java.nio.file.Paths.get(dataPath);
		String fileName = javaDataPath.getFileName().toString();
		this.convertedTestFile = FilenameUtils.removeExtension(fileName) + "_testConverted.arff";
		try{
			//readConvertTest(dataPath, this.convertedTestFile);
			readDivideTest(dataPath,this.convertedTestFile);
		}
		catch(Exception e)
		{
			System.out.println("Could not process the onservation file!");
		}
		this.data = DynamicDataStreamLoader.loadFromFile(this.convertedTestFile);
		// Load the DBN model
		try{
			this.DBN = DynamicBayesianNetworkLoader.loadFromFile(DBN_path);
			this.origDBN = DynamicBayesianNetworkLoader.loadFromFile(DBN_path);
			// Write the DBN to a text file
			BufferedWriter fos = new BufferedWriter(new FileWriter("testNet.txt"));
			fos.append(this.DBN.toString());
			fos.close();
			readPriors(priorPath);
			
		}
		catch(Exception e)
		{
			System.out.println("Could not read the DBN file!");
		}
		
		// Setting up the inference algorithm
		//FactoredFrontierForDBN inference = new FactoredFrontierForDBN(new VMP());
		DynamicVMP inference = new DynamicVMP();
		InferenceEngineForDBN.setInferenceAlgorithmForDBN(inference);
		InferenceEngineForDBN.setModel(this.DBN);
		this.ignore_unseen_samples = ignore_unseen_samples;
		if(ignore_unseen_samples){
			try{
				this.determineUnseenLinks(train_data_path);
			}
			catch(Exception e){
				System.out.println(e);
			}
		}
	}

	public void readPriors(String priorPath) throws FileNotFoundException,IOException
	{
		System.out.println("Reading the prior file...");
		FileInputStream fis = new FileInputStream(priorPath);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		// Read number of edges

		this.priors = new double[this.nWays];
		String cur_line = null;
		
		while((cur_line=br.readLine()) != null)
		{
			String[] splits = cur_line.split(",");
			int id = Integer.parseInt(splits[0]);
			double cur_prior = Double.parseDouble(splits[1]);
			this.priors[id]=cur_prior;
		}
		br.close();
		System.out.println("Done!");
	}

//	public void evaluate2()
//	{
//		// Read all average observations
//		List<DynamicDataInstance> allObservations = new ArrayList<DynamicDataInstance>();
//		for(DynamicDataInstance instance : this.data)
//			allObservations.add(instance);
//		
//		// Shuffle data
//		Collections.shuffle(allObservations);
//		
//		// Devide data into test and train splits
//		int n_train = Math.floor(allObsevations);
//	}
	public void evaluate()
	{

		
		int n_samples = this.carTestPaths.size();
		double[] predictedTime = new double[n_samples];
		double[] realTime = new double[n_samples];
		double[] averageTime = new double[n_samples];
		double[] upToNowAvg = new double[this.nWays];
		int[] upToNowCount = new int[this.nWays];
		
		// Per time step Errors
		double[][] oursPerStep = new double[n_samples][this.nTimeSteps];
		double[][] baselinePerStep = new double[n_samples][this.nTimeSteps];
		double[][] gtPerStep = new double[n_samples][this.nTimeSteps];
		
		// Predict the travel time for each of the time steps
		int t = 0;
		for(DynamicDataInstance instance : this.data)
		{
		
			InferenceEngineForDBN.addDynamicEvidence(instance);

			// Run the inference algorithm
			InferenceEngineForDBN.runInference();
			
			// Compute the error for all samples
			for(int i=0;i<n_samples;i++)
			{
				// get all samples from the current time
				List<Observation> curObs = this.carTestPaths.get(i).getObservationsTimeT(t);
				for(Observation obs:curObs)
				{
					int linkID = obs.getId();
					if(this.ignore_unseen_samples && this.ignore_link_flags[linkID])
						continue;
					realTime[i] += obs.getValue();
					gtPerStep[i][t] += obs.getValue();
					
					Variable curVariable = DBN.getDynamicVariables().getVariableByName("W"+String.valueOf(linkID));
					Variable curHidden = DBN.getDynamicVariables().getVariableByName("W"+String.valueOf(linkID)+"h");	
					// Set the priors for time0
					//Variable curVariable2 = origDBN.getDynamicVariables().getVariableByName("W"+String.valueOf(linkID));
					Normal_MultinomialParents conditional_dists = this.origDBN.getConditionalDistributionTimeT(curVariable);
					//Normal_MultinomialParents conditional_dists2 = this.origDBN.getConditionalDistributionTime0(curVariable2);
					//Get the prediction for current variable
					Multinomial curDist = InferenceEngineForDBN.getFilteredPosterior(curHidden);
					int cur_traffic = 0;
					int nStates = curDist.getProbabilities().length;
					double maxProb = -1;
					for(int s=0;s<nStates;s++)
					{
						if(curDist.getProbabilities()[s]>maxProb)
						{
							cur_traffic = s;
							maxProb = curDist.getProbabilities()[s];
						}
					}
					

 					
 					double avg_estimate = instance.getValue(curVariable);
 					
 					if(!Double.isNaN(avg_estimate)){
 						upToNowAvg[linkID]+=avg_estimate;
 						upToNowCount[linkID]++;
 					}
 					if(upToNowCount[linkID]==0)
 						avg_estimate = this.priors[linkID];
 					else
 						avg_estimate = upToNowAvg[linkID]/upToNowCount[linkID];
 					averageTime[i] += avg_estimate;
 					baselinePerStep[i][t] += avg_estimate;
 					
 					double estimate = conditional_dists.getNormal(cur_traffic).getMean();
					
 					if(this.weightedAverage)
 					{
 						if(estimate<=0)
 	 						estimate = this.priors[linkID];
 						else if(upToNowAvg[linkID]>0){
 							double sigma = Math.sqrt(conditional_dists.getNormal(cur_traffic).getVariance());
 							double coef = 0.01;
 							estimate = estimate/(coef*sigma) + (upToNowAvg[linkID]/upToNowCount[linkID])/sigma;
 							estimate = estimate/(1/(coef*sigma)+upToNowCount[linkID]/sigma);
 						}
 					}
 					else{
 						if(estimate<=0)
 	 						estimate = this.priors[linkID];
 					}
 					predictedTime[i] += estimate;
 					oursPerStep[i][t] += estimate;
				}
			}
			t++;

			
		}
		// Total error
		double error = 0;
		double avg_error = 0;
		double path_time = 0;
		int fivemin_counter =0;
		

		
		for(int i=0;i<n_samples;i++)
		{
			if(realTime[i]>600000)
			{
				error += Math.abs(realTime[i]-predictedTime[i]);
				avg_error += Math.abs(realTime[i]-averageTime[i]);
				path_time += realTime[i];
				fivemin_counter++;
			}
		}
		
		double avgOurError = error/fivemin_counter;
		double avgBaselineError = avg_error/fivemin_counter;
		double avgTripDuration = path_time/fivemin_counter;
		System.out.println(fivemin_counter);
		System.out.println(avgOurError);
		System.out.println(avgBaselineError);
		System.out.println(avgTripDuration);
		System.out.println(avgOurError/avgTripDuration);
		System.out.println(avgBaselineError/avgTripDuration);

		//Error per time step
		double[] eOursPerStep = new double[this.nTimeSteps];
		double[] eBaselinePerStep = new double[this.nTimeSteps];
		double[] gtPerStepSum = new double[this.nTimeSteps];
		int[] countPerStep = new int[this.nTimeSteps];
		
		for(int i=0;i<n_samples;i++)
		{
			if(realTime[i]>300000)
			{
				for(int ts=0;ts<this.nTimeSteps;ts++)
				{
					if(gtPerStep[i][ts]==0)
						continue;
					eOursPerStep[ts] += Math.abs(gtPerStep[i][ts]-oursPerStep[i][ts]);
					eBaselinePerStep[ts] += Math.abs(gtPerStep[i][ts]-baselinePerStep[i][ts]);
					gtPerStepSum[ts] += gtPerStep[i][ts];
					countPerStep[ts]++;
				}
			}
		}
		for(int ts=0;ts<this.nTimeSteps;ts++)
		{
			gtPerStepSum[ts]/=countPerStep[ts];
			
			eOursPerStep[ts]/=countPerStep[ts];
			eOursPerStep[ts]/=gtPerStepSum[ts];
			
			eBaselinePerStep[ts]/=countPerStep[ts];
			eBaselinePerStep[ts]/=gtPerStepSum[ts];
		}
		System.out.println(eOursPerStep);	
		System.out.println(eBaselinePerStep);
		
		
	}
	
	public void determineUnseenLinks(String train_path) throws FileNotFoundException,IOException{
		this.ignore_link_flags = new boolean[this.nWays];
		for(int i=0;i<this.nWays;i++)
			this.ignore_link_flags[i] = true;
		FileInputStream fis = new FileInputStream(train_path);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));// read all paths
		String prefix = "";
		String cur_line = null;
		while((cur_line=br.readLine()) != null)
		{
			// Skip the headers
			if(cur_line.startsWith("@")){
				prefix+= cur_line +"\r\n";
				continue;
			}
			//Skip empty lines
			if(cur_line.length()==0)
				continue;
			// Split the string...
			String[] str_splits = cur_line.split(",");
			for(int i=2;i<str_splits.length;i++)
			{
				if(!str_splits[i].equals("?"))
				{
					this.ignore_link_flags[i-2] = false;
					
				}
					
			}			
		}
		br.close();
		
	}
	
	public void readDivideTest(String test_path,String out_path) throws FileNotFoundException, IOException
	{
		this.carTestPaths = new ArrayList<Path>();
		List<Path> allPaths = new ArrayList<Path>();
		FileInputStream fis = new FileInputStream(test_path);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		
		// read all paths
		String prefix = "";
		String cur_line = null;
		while((cur_line=br.readLine()) != null)
		{
			// Skip the headers
			if(cur_line.startsWith("@")){
				prefix+= cur_line +"\r\n";
				continue;
			}
			//Skip empty lines
			if(cur_line.length()==0)
				continue;
			// Split the string...
			String[] str_splits = cur_line.split(",");
			
			// Number of ways is equal to number of columns -2
			if(this.nWays==-1)
				this.nWays = str_splits.length - 2;
				
			int seqID = Integer.parseInt(str_splits[0]);
			// Expand the carPaths list if necessary
			while(allPaths.size()<=seqID)
				allPaths.add(new Path());
			
			int time_step = Integer.parseInt(str_splits[1]);
			
			
			// Update the number of time steps
			if(time_step>this.nTimeSteps)
				this.nTimeSteps = time_step;
			
			
			// Expand the list if we have more time steps...
			
			for(int i=2;i<str_splits.length;i++)
			{
				if(!str_splits[i].equals("?"))
				{
					// Add time for averaging
					double cur_duration = Double.parseDouble(str_splits[i]);					
					// Add way to the corresponding path
					Observation curObs = new Observation(i-2,cur_duration,time_step);
					allPaths.get(seqID).addObservation(curObs);
				}
					
			}			
			
		}
		br.close();
		
		// Deviding the set into test and realtime estimation sets
		
		int nTraining = (int)Math.floor(0.7*allPaths.size());
		// Shuffle the paths
		Collections.shuffle(allPaths);
		
		// Create test set
		for(int i=nTraining;i<allPaths.size();i++)
			this.carTestPaths.add(allPaths.get(i));
		
		// Create the realtime estimate
		double[][] estimates = new double[this.nTimeSteps+1][this.nWays];
		int[][] counts = new int[this.nTimeSteps+1][this.nWays];
		for(int i=0;i<nTraining;i++)
		{
			List<Observation> curObs = allPaths.get(i).getPath();
			for(Observation obs:curObs)
			{
				estimates[obs.getTime()][obs.getId()] += obs.getValue();
				counts[obs.getTime()][obs.getId()]++;
			}
		}
		
		BufferedWriter fos = new BufferedWriter(new FileWriter(out_path));
		// Write the headers....
		fos.append(prefix);
		for(int i=0;i<this.nTimeSteps;i++)
		{
			cur_line = "0,"+String.valueOf(i);
			for(int j=0;j<this.nWays;j++)
			{
				if(counts[i][j]==0)
					cur_line += ",?";
				else
					cur_line += ","+String.valueOf(estimates[i][j]/counts[i][j]);
			}
			cur_line+="\r\n";
			fos.append(cur_line);
			fos.flush();
		}
		
		fos.close();
		System.out.println("Done!");
	}
	
	public void enableWeightedAveraging()
	{
		this.weightedAverage = true;
	}
	
	public void disableWeightedAveraging()
	{
		this.weightedAverage = false;
	}
	public void readConvertTest(String test_path,String out_path) throws FileNotFoundException, IOException
	{		
		this.carTestPaths = new ArrayList<Path>();
		
		FileInputStream fis = new FileInputStream(test_path);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		
		
		List<double[]> tests = new ArrayList<double[]>();
		List<int[]> counts = new ArrayList<int[]>();
		String prefix = "";
		String cur_line = null;
		while((cur_line=br.readLine()) != null)
		{
			// Skip the headers
			if(cur_line.startsWith("@")){
				prefix+= cur_line +"\r\n";
				continue;
			}
			//Skip empty lines
			if(cur_line.length()==0)
				continue;
			// Split the string...
			String[] str_splits = cur_line.split(",");
			
			// Number of ways is equal to number of columns -2
			if(this.nWays==-1)
				this.nWays = str_splits.length - 2;
			
			
			int seqID = Integer.parseInt(str_splits[0]);
			// Expand the carPaths list if necessary
			while(this.carTestPaths.size()<=seqID)
				carTestPaths.add(new Path());
			
			int time_step = Integer.parseInt(str_splits[1]);
			// Update the number of time steps
			if(time_step>this.nTimeSteps)
				this.nTimeSteps = time_step;
			
			
			// Expand the list if we have more time steps...
			while(tests.size()<=time_step)
			{
				tests.add(new double[this.nWays]);
				counts.add(new int[this.nWays]);
			}
			for(int i=2;i<str_splits.length;i++)
			{
				if(!str_splits[i].equals("?"))
				{
					// Add time for averaging
					double cur_duration = Double.parseDouble(str_splits[i]);
					tests.get(time_step)[i-2] += cur_duration;
					counts.get(time_step)[i-2]++;
					
					// Add way to the corresponding path
					Observation curObs = new Observation(i-2,cur_duration,time_step);
					this.carTestPaths.get(seqID).addObservation(curObs);
				}
					
			}			
			
		}
		br.close();
		// Writing the new file
		BufferedWriter fos = new BufferedWriter(new FileWriter(out_path));
		// Write the headers....
		fos.append(prefix);
		for(int i=0;i<tests.size();i++)
		{
			cur_line = "0,"+String.valueOf(i);
			for(int j=0;j<counts.get(i).length;j++)
			{
				if(counts.get(i)[j]==0)
					cur_line += ",?";
				else
					cur_line += ","+String.valueOf(tests.get(i)[j]/counts.get(i)[j]);
			}
			cur_line+="\r\n";
			fos.append(cur_line);
			fos.flush();
		}
		
		fos.close();
		System.out.println("Done!");
	}
}
