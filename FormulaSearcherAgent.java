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

package examples.linearRegression;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class FormulaSearcherAgent extends Agent {
	// The subject to look for
	private String targetSubject;
	// The list of known linear-regression agents
	private AID[] linearRegressionAgents;

	// Put agent initializations here
	protected void setup() {
		// Printout a welcome message
		System.out.println("Hallo! FormulaSearcher "+getAID().getName()+" is ready.");

		// Get the subject as a start-up argument
		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			targetSubject = (String) args[0];
			System.out.println("Target subject is "+targetSubject);

			// Add a TickerBehaviour that schedules a request to linear-regression agents every minute
			addBehaviour(new TickerBehaviour(this, 7000) {
				protected void onTick() {
					System.out.println("Trying get " + targetSubject + " linear regression formula");
					// Update the list of linear-regression agents
					DFAgentDescription template = new DFAgentDescription();
					ServiceDescription sd = new ServiceDescription();
					sd.setType("linear-regression");
					template.addServices(sd);
					try {
						DFAgentDescription[] result = DFService.search(myAgent, template);
						System.out.println("Found the following linear-regression agents:");
						linearRegressionAgents = new AID[result.length];
						for (int i = 0; i < result.length; ++i) {
							linearRegressionAgents[i] = result[i].getName();
							System.out.println(linearRegressionAgents[i].getName());
						}
					}
					catch (FIPAException fe) {
						fe.printStackTrace();
					}

					// Perform the request
					myAgent.addBehaviour(new RequestPerformer());
				}
			} );
		}
		else {
			// Make the agent terminate
			System.out.println("No target subject specified");
			doDelete();
		}
	}

	// Put agent clean-up operations here
	protected void takeDown() {
		// Printout a dismissal message
		System.out.println("Formula-searcher-agent "+getAID().getName()+" terminating.");
	}

	/**
	   Inner class RequestPerformer.
	   This is the behaviour used by Book-buyer agents to request seller
	   agents the target book.
	 */
	private class RequestPerformer extends Behaviour {
		private AID mostReliable; // The agent who provides more observations
		private int maxNumObs;  // Max number of observations
		private int repliesCnt = 0; // The counter of replies from seller agents
		private MessageTemplate mt; // The template to receive replies
		private int step = 0;

		public void action() {
			switch (step) {
			case 0:
				// Send the cfp to all sellers
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < linearRegressionAgents.length; ++i) {
					cfp.addReceiver(linearRegressionAgents[i]);
				}
				cfp.setContent(targetSubject);
				cfp.setConversationId("book-trade");
				cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
				myAgent.send(cfp);
				// Prepare the template to get proposals
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			case 1:
				// Receive all proposals/refusals from linear-regression agents
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// Reply received
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// This is an offer
						int numObs = Integer.parseInt(reply.getContent());
						if (mostReliable == null || numObs > maxNumObs) {
							// This is the max number of observations at present
							maxNumObs = numObs;
							mostReliable = reply.getSender();
						}
					}
					repliesCnt++;
					if (repliesCnt >= linearRegressionAgents.length) {
						// We received all replies
						step = 2;
					}
				}
				else {
					block();
				}
				break;
			case 2:
				// Send the CFP order to the linear-regression agent that provided the best offer
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(mostReliable);
				order.setContent(targetSubject);
				order.setConversationId("book-trade");
				order.setReplyWith("order"+System.currentTimeMillis());
				myAgent.send(order);
				// Prepare the template to get the purchase order reply
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
						MessageTemplate.MatchInReplyTo(order.getReplyWith()));
				step = 3;
				break;
			case 3:
				// Receive the purchase order reply
				reply = myAgent.receive(mt);
				if (reply != null) {
					// Purchase order reply received
					if (reply.getPerformative() == ACLMessage.INFORM) {
						// Purchase successful. We can terminate
						System.out.println(targetSubject+" formula successfully gotted from agent "+reply.getSender().getName());
						System.out.println("Number of observations = "+maxNumObs);
						myAgent.doDelete();
					}
					else {
						System.out.println("Attempt failed: something went wrong.");
					}

					step = 4;
				}
				else {
					block();
				}
				break;
			}
		}

		public boolean done() {
			if (step == 2 && mostReliable == null) {
				System.out.println("Attempt failed: "+targetSubject+" observations not available");
			}
			return ((step == 2 && mostReliable == null) || step == 4);
		}
	}  // End of inner class RequestPerformer
}
