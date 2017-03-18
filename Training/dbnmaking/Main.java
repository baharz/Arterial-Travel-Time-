package dbnmaking;
import java.io.IOException;

import dbnmaking.TravelTime;
import eu.amidst.dynamic.io.DynamicBayesianNetworkWriter;
import eu.amidst.dynamic.models.DynamicBayesianNetwork;
public class Main {
	public static void main(String[] args)
	{

		boolean append_attribs =true;
		//String observation_path = "dbn_pmpeak_junejuly_contimesteps.arff";
		String observation_path = "data/test_all.arff";
		String adj_mat_path = "mtxsmall.txt";
		String prior_path = "prior.txt";
		
		//Append headers
		if(append_attribs)
		{
			TravelTime travelTime = new TravelTime(adj_mat_path);
			try{
				travelTime.appendAttribs(observation_path);
			}
			catch(Exception e)
			{
				System.out.println("Error pasrinf the observation file");
			}
			System.out.println("Hello!");
		}
		
		//Create the travelTime object
		TravelTime travelTime = new TravelTime(adj_mat_path,observation_path,prior_path,2);
		
		DynamicBayesianNetwork dbnLearnt = travelTime.trainDBN();
		try{
			//DynamicBayesianNetworkWriter.save(dbnLearnt, "LearntDBN_SVB_pmpeak_junejuly_cont.dbn");
			DynamicBayesianNetworkWriter.save(dbnLearnt, "LearntDBN_SVB_pmpeak_all_from0_all.dbn");
		}
		catch(IOException e)
		{
			System.out.println("Could not save the network!");
		}
		

		
	}
}
