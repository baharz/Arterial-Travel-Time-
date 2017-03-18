package dbnmaking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import eu.amidst.core.datastream.Attribute;
import eu.amidst.core.datastream.DataOnMemory;
import eu.amidst.core.datastream.DataStream;
import eu.amidst.core.distribution.Normal_MultinomialParents;
import eu.amidst.core.inference.messagepassing.VMP;

import eu.amidst.dynamic.datastream.DynamicDataInstance;
import eu.amidst.dynamic.io.DynamicDataStreamLoader;
import eu.amidst.dynamic.learning.parametric.ParallelMLMissingData;
import eu.amidst.dynamic.learning.parametric.ParallelMaximumLikelihood;
import eu.amidst.dynamic.learning.parametric.ParameterLearningAlgorithm;
import eu.amidst.dynamic.learning.parametric.bayesian.BayesianLearningAlgorithm;
import eu.amidst.dynamic.learning.parametric.bayesian.SVB;
import eu.amidst.dynamic.models.DynamicBayesianNetwork;
import eu.amidst.dynamic.models.DynamicDAG;
import eu.amidst.dynamic.variables.DynamicVariables;
import eu.amidst.core.variables.Variable;
import utils.Observation;
import utils.Path;
public class TravelTime {
	
	// Variables
	private int nAdditionalVars;// Additional variables (e.g. weather)
	private int nWays; // Number of ways
	private boolean[][] adjMatrix;
	private double[] priors;
	private List<Variable> ways;
	private List<Variable> hiddens;
	private List<Variable> hiddenInterfaces;
	private DynamicBayesianNetwork DBN;
	private DataStream<DynamicDataInstance> data;
	private List<Path> carTestPaths;
	private int nState;

	//Constructors
	public TravelTime(){}
	public TravelTime(String adjMatrixPath)
	{
		try{
			this.createAdjMatrix(adjMatrixPath);
		}
		catch(Exception e){
			System.out.println("There was a problem loading the adj matrix");
		}
	}
	public TravelTime(String adjMatrixPath, String observation_path, String prior_path, int nState)
	{
		this.nState = nState;
		try{
			this.createAdjMatrix(adjMatrixPath);
		}
		catch(Exception e){
			System.out.println("There was a problem loading the adj matrix");
		}
		// Reading data
		this.data = DynamicDataStreamLoader.loadFromFile(observation_path);
		createDBN();
		
		// Setting priors
		try{
			setPriors(prior_path);
		}
		catch(Exception e){
			System.out.println("There was a problem processing the priors file");
		}

	}
	
	
	public void setPriors(String priorPath) throws FileNotFoundException,IOException
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
		
		System.out.println("Setting he priors...");
		for(int i=0;i<this.nWays;i++)
		{
			Variable curVar = this.DBN.getDynamicVariables().getVariableByName("W"+String.valueOf(i));
			
			// Set the priors for time0
			Normal_MultinomialParents conditional_dists = this.DBN.getConditionalDistributionTime0(curVar);
			int n_dists = conditional_dists.getNormalDistributions().size();
			for(int j=0;j<n_dists;j++)
			{
				conditional_dists.getNormalDistributions().get(j).setMean(priors[i]);
			}
			
			// Set the priors for timeT
			conditional_dists = this.DBN.getConditionalDistributionTimeT(curVar);
			n_dists = conditional_dists.getNormalDistributions().size();
			for(int j=0;j<n_dists;j++)
			{
				conditional_dists.getNormalDistributions().get(j).setMean(priors[i]);
			}
		}
		
	}

	// Member functions
	public void createDBN()
	{
		System.out.println("Creating DBN...");
		// Newing the internal variables
		DynamicVariables dynamicVariables = new DynamicVariables();
		this.ways = new ArrayList<Variable>();
		this.hiddens = new ArrayList<Variable>();
		this.hiddenInterfaces = new ArrayList<Variable>();
		// Getting way attributes
		for(int i=0;i<nWays;i++)
		{
			// Get attribute by name (The way name is "W"+id)
			Attribute cur_attrib = data.getAttributes().getAttributeByName("W"+String.valueOf(i));
			// The main dynamic variable 
			this.ways.add(dynamicVariables.newDynamicVariable(cur_attrib));
			// The hidden variable related to the way
			Variable cur_hidden = dynamicVariables.newMultinomialDynamicVariable("W"+String.valueOf(i)+"h", nState);
			this.hiddens.add(cur_hidden);
			// The hidden variable in the previous time step
			this.hiddenInterfaces.add(dynamicVariables.getInterfaceVariable(cur_hidden));
		}
		// TODO: YOU SHOULD ADD ANY EXTRA VARIABLES HERE
		
		dynamicVariables.block();
		
		// Creating DAG
		DynamicDAG DAG = new DynamicDAG(dynamicVariables);
		
		//***** RELATIONSHIPS
		// 1) Adding hidden to each way and each hidden to its interface
		for(int i=0;i<nWays;i++)
		{
			DAG.getParentSetTimeT(this.ways.get(i)).addParent(this.hiddens.get(i));
			DAG.getParentSetTimeT(this.hiddens.get(i)).addParent(this.hiddenInterfaces.get(i));
		}
		// 3) Adding hidden relationships
		for(int i=0;i<nWays;i++)
		{
			for(int j=0;j<nWays;j++)
			{
				if(this.adjMatrix[i][j]&& i!=j)
				{
					//They are connected
					DAG.getParentSetTimeT(this.hiddens.get(j)).addParent(this.hiddenInterfaces.get(i));
				}
			}
		}
		// Create the DBN
		
		this.DBN = new DynamicBayesianNetwork(DAG);
		System.out.println("Done!");
		
	}
	
	public DynamicBayesianNetwork trainDBN()
	{
		System.out.println("Learning parameters...");
		BayesianLearningAlgorithm parallelMaximumLikelihood = new SVB();
		//ParameterLearningAlgorithm parallelMaximumLikelihood=new ParallelMaximumLikelihood();
	    //parallelMaximumLikelihood.setWindowsSize(1000);
	    
	    parallelMaximumLikelihood.setDynamicDAG(this.DBN.getDynamicDAG());
	    parallelMaximumLikelihood.initLearning();
//	    int batchCounter=0;
//	    for (DataOnMemory<DynamicDataInstance> batch : data.iterableOverBatches(1000)){
//	    	System.out.println("Batch#:"+String.valueOf(batchCounter));
//	    	parallelMaximumLikelihood.updateModel(batch);
//	    	batchCounter++;
//        }

	    
	    

	    parallelMaximumLikelihood.updateModel(this.data);
	    DynamicBayesianNetwork dbnLearnt = parallelMaximumLikelihood.getLearntDBN();
	    System.out.println("Done!");
	    return dbnLearnt;
	    
	}

	
	public void appendAttribs(String file_path) throws IOException
	{
		// Create a backup for the observation
		File newFile = new File(file_path + ".backup");
		File oldFile = new File(file_path);
		oldFile.renameTo(newFile);
		
		int n_ways = this.nWays;
		RandomAccessFile file= new RandomAccessFile(oldFile, "rw");
		file.seek(0);
		file.write("@relation traveltime\r\n".getBytes());
		// Write attribute headers
		file.write("@attribute SEQUENCE_ID real\r\n".getBytes());
		file.write("@attribute TIME_ID real\r\n".getBytes());
		for(int i=0;i<n_ways;i++)
		{
			String cur_name = "@attribute W"+ String.valueOf(i)+ " real\r\n";
			file.write(cur_name.getBytes());
		}
		// Write data header
		file.write("@data\r\n".getBytes());
		//Copy lines from the original file
		FileInputStream fis = new FileInputStream(newFile);
		String line = null;
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		while ((line = br.readLine()) != null) {
			file.write((line+"\r\n").getBytes());
		}
		file.close();
		br.close();
	}
	
	public void createAdjMatrix(String file_path) throws FileNotFoundException, IOException
	{	
		System.out.println("Loading the adj matrix...");
		FileInputStream fis = new FileInputStream(file_path);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		// Read number of edges
		String cur_line = br.readLine();
		this.nWays = Integer.parseInt(cur_line);
		
		this.adjMatrix = new boolean[this.nWays][this.nWays];
		while((cur_line=br.readLine()) != null)
		{
			String[] way_ids = cur_line.split(",");
			int way0 = Integer.parseInt(way_ids[0]);
			int way1 = Integer.parseInt(way_ids[1]);
			this.adjMatrix[way0][way1]=true;
		}
		br.close();
		System.out.println("Done!");
	}
	
	// Setters and Getters
	public int getnWays() {
		return nWays;
	}
	public void setnWays(int nWays) {
		this.nWays = nWays;
	}
	public boolean[][] getAdjMatrix() {
		return adjMatrix;
	}
	public void setAdjMatrix(boolean[][] adjMatrix) {
		this.adjMatrix = adjMatrix;
	}
}
