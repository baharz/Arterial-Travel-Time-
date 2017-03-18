package dbnmaking;

import java.io.IOException;

import eu.amidst.dynamic.io.DynamicBayesianNetworkWriter;
import eu.amidst.dynamic.models.DynamicBayesianNetwork;

public class MainTest {
	
	public static void main(String[] args)
	{

		/***** VERY OLD
		 * 	//String test_path = "dbn_pmpeak_all_contimesteps.arff";
			//String dbn_path = "LearntDBN_SVB_pmpeak_junejuly.dbn";
			//String dbn_path = "LearntDBN_SVB_pmpeak_all_from0.dbn";
			//String dbn_path = "LearntDBN_SVB_pmpeak_all_from0_threeSteps.dbn";
		 */
		/**** OLD
		 * 	//String test_path = "./dbn_pmpeak_junetest.arff";
			//String dbn_path = "LearntDBN_SVB_pmpeak_all_contimesteps.dbn";
		 */

		String test_path = "./data/test_all.arff";
		//String test_path = "./dbn_pmpeak_junetest.arff";
		//String dbn_path = "LearntDBN_SVB_pmpeak_all_from0.dbn";
		
		String train_path = "./data/train_all.arff";
		String dbn_path = "LearntDBN_SVB_pmpeak_all_from0_all.dbn";
		String prior_path = "prior.txt";
		boolean ignore_unseen_links = false;
		
		//Create the travelTime object
		TravelTimeTest travelTime = new TravelTimeTest(dbn_path,test_path,prior_path, ignore_unseen_links, train_path);
		travelTime.enableWeightedAveraging();
		travelTime.evaluate();
		

		
	}
}
