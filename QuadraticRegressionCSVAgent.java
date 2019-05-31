/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A.

GNU Lesser General Public License

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation,
version 2.1 of the License.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the
Free Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA  02111-1307, USA.
 *****************************************************************/

package examples.QuadraticRegression;

import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;
import com.opencsv.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

// to resolve the system of equiations 
import Jama.Matrix;
import java.lang.Math.*;

public class QuadraticRegressionCSVAgent extends Agent {
	/// Initialize variables
	Random  rnd = new Random();
	ArrayList<Double> observationsX = new ArrayList<Double>();
	ArrayList<Double> observationsY = new ArrayList<Double>();
	String[] observation = new String[2];
	String fileName;
	String fileNamePre;
	CSVWriter writer;

	// The GUI by means of which the user can add books in the catalogue
	private QuadraticRegressionCSVAgentGUI myGui;

	// Put agent initializations here
	protected void setup() {
		/// Set CSV Files Paths
		fileName = "observations.csv";
		fileNamePre = "predictions.csv";

		// Create and show the GUI
		myGui = new QuadraticRegressionCSVAgentGUI(this);
		myGui.showGui();

		// Register the linear-regression service in the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Quadratic-Regression");
		sd.setName("pipo-linear-regression");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}

		// Add the behaviour serving queries from buyer agents
		addBehaviour(new OfferRequestsServer());

		// Add the behaviour serving purchase orders from buyer agents
		addBehaviour(new PurchaseOrdersServer());
	}

	// Put agent clean-up operations here
	protected void takeDown() {
		// Deregister from the yellow pages
		try {
			DFService.deregister(this);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
		// Close the GUI
		myGui.dispose();
		// Printout a dismissal message
		System.out.println("Seller-agent "+getAID().getName()+" terminating.");
	}

    // This is invoked by the GUI when the user adds a new observation
	public void updateCatalogue(final Double xValue, final Double yValue) {
		addBehaviour(new OneShotBehaviour() {
			public void action() {
				System.out.println("Added observation X = " + xValue + " Y = " + yValue);
                observationsX.add(xValue);
                observationsY.add(yValue);

				/// Write to CSV
                try {
                    writer = new CSVWriter(new FileWriter(fileName, true));

                    observation[0] = Double.toString(xValue);
                    observation[1] = Double.toString(yValue);
                    writer.writeNext(observation);

                    writer.close();
				} catch (IOException e){
                    e.getMessage();
                }
			}
		} );
	}

	/**
	   Inner class OfferRequestsServer.
	   This is the behaviour used by linear-regression agents to serve incoming requests
	   for offer from requester agents.
	   The agent replies with a PROPOSE message specifying the amount of observations.
	   If observations are 0 then REFUSE.
	 */
	private class OfferRequestsServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// CFP Message received. Process it
				String subject = msg.getContent();
				ACLMessage reply = msg.createReply();

				/// Check if was found
				if (observationsX.size() > 0) {
					// Observations are available. Reply with the number
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setContent(String.valueOf(observationsX.size()));
				}
				else {
					// NOT observations available
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}  // End of inner class OfferRequestsServer

	/**
	   Inner class PurchaseOrdersServer.
	   This is the behaviour used by Book-seller agents to serve incoming
	   offer acceptances (i.e. purchase orders) from buyer agents.
	   The seller agent removes the purchased book from its catalogue
	   and replies with an INFORM message to notify the buyer that the
	   purchase has been sucesfully completed.
	 */
	private class PurchaseOrdersServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// ACCEPT_PROPOSAL Message received. Process it
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();

                /// Calculate formula and write 10 predictions
                ArrayList<Double> xy = new ArrayList<Double>();
                ArrayList<Double> xx = new ArrayList<Double>();
                ArrayList<Double> yy = new ArrayList<Double>();

                Double sumX = 0.0;
                Double sumY = 0.0;
                Double sumX2 = 0.0;
		Double sumX3 = 0.0;
                Double sumXY = 0.0; // RECYCLED JAJA
                Double sumX2Y = 0.0; // X2
		Double sumX4 = 0.0;

                int n = observationsX.size();

                for(int i = 0; i < n; i++){

			sumX += observationsX.get(i);
			sumY += observationsY.get(i);
			sumX2 += observationsX.get(i) * observationsX.get(i);
			sumX3 += observationsX.get(i) * observationsX.get(i) * observationsX.get(i);
			sumXY += observationsX.get(i) * observationsY.get(i);
			sumX2Y += observationsX.get(i) * observationsX.get(i) * observationsY.get(i);
			sumX4 += observationsX.get(i) * observationsX.get(i) * observationsX.get(i) * observationsX.get(i);

		}

		        double[][] lhsArray = {{sumX2, sumX, n}, {sumX3, sumX2, sumX}, {sumX4, sumX3, sumX2}};
			double[] rhsArray = {sumY, sumXY, sumX2Y};

			Matrix lhs = new Matrix(lhsArray);
			Matrix rhs = new Matrix(rhsArray, 3);

			Matrix ans = lhs.solve(rhs);

			System.out.println("x = " + Math.round(ans.get(0, 0)));
			System.out.println("y = " + Math.round(ans.get(1, 0)));
			System.out.println("z = " + Math.round(ans.get(2, 0)));


                Double a = 0.0;
                Double b = 0.0;
                Double c = 0.0;
		// JAMA



                Double max = observationsX.get(0);
                Double min = observationsX.get(0);

                Double diff;
                int random;


		// oks
                for(int i = 0; i < n; i++){
                    xy.add(observationsX.get(i) * observationsY.get(i));
                    xx.add(observationsX.get(i) * observationsX.get(i));
                    yy.add(observationsY.get(i) * observationsY.get(i));

                    if(max < observationsX.get(i)){
                        max = observationsX.get(i);
                    }
                    if(min > observationsX.get(i)){
                        min = observationsX.get(i);
                    }
                }

                System.out.println("sum x: " + sumX);
                System.out.println("sum y: " + sumY);

                /// Write predictions
                try {
                    writer = new CSVWriter(new FileWriter(fileNamePre, true));
                    diff = max - min;

                    for(int i = 0; i < 10; i++){
                        random = (int)(rnd.nextDouble() * (diff*2) + (min - diff/2));

                        observation[0] = Integer.toString(random);
                        observation[1] = Double.toString(a + b * random);
                        writer.writeNext(observation);
                    }

                    writer.close();
				} catch (IOException e){
                    e.getMessage();
                }

                /// Send reply
				if (a != null && b != null) {
					reply.setPerformative(ACLMessage.INFORM);
					System.out.println("Formula: y = " + a + " + " + b + "x");
				}
				else {
					// The requested book has been sold to another buyer in the meanwhile .
					reply.setPerformative(ACLMessage.FAILURE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}  // End of inner class OfferRequestsServer
}
