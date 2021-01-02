package group12;

import java.util.List;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.Domain;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.EndNegotiation;
import genius.core.actions.Offer;
import genius.core.boaframework.SortedOutcomeSpace;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;

/**
 * A simple example agent that makes random bids above a minimum target utility.
 *
 * @author Tim Baarslag
 */
public class MyAgent extends AbstractNegotiationParty
{
    private static double MINIMUM_TARGET = 0.8;
    private static double TARGET_UTILITY;
    private static double THRESHOLD;
    private Bid lastOffer;

    /**
     * Initializes a new instance of the agent.
     */
    @Override
    public void init(NegotiationInfo info)
    {
        super.init(info);
        AbstractUtilitySpace utilitySpace = info.getUtilitySpace();

        //UtilitySpace: domain and preference profile
        AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;

        //List of issues
        List<Issue> issues = additiveUtilitySpace.getDomain().getIssues();

        for (Issue issue : issues) {
            int issueNumber = issue.getNumber();
            System.out.println(">> " + issue.getName() + " weight: " + additiveUtilitySpace.getWeight(issueNumber));

            // Assuming that issues are discrete only
            IssueDiscrete issueDiscrete = (IssueDiscrete) issue;

            //thats the evaluation function for all the values of the issue (the nominator in the fraction used to
            //calculate the utility of the agent, thats the function evalj(oi) bit from sumn(wji(evalj(oi)/max(evalj(Ii))))
            EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) additiveUtilitySpace.getEvaluator(issueNumber);

            for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
                System.out.println(valueDiscrete.getValue());

                //This gives the number of the value, no need to keep a HashMap!
                System.out.println("Evaluation(getValue): " + evaluatorDiscrete.getValue(valueDiscrete));
                try {
                    //This gives the evaluation of the value
                    System.out.println("Evaluation(getEvaluation): " + evaluatorDiscrete.getEvaluation(valueDiscrete));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        TARGET_UTILITY = getUtility(getMaxUtilityBid());
        THRESHOLD = (getUtility(getMaxUtilityBid())+getUtility(getMinUtilityBid()))/2;
        System.out.println("THRESHOLD: " + THRESHOLD);
    }

    private Bid getMaxUtilityBid() {
        try {
            return utilitySpace.getMaxUtilityBid();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Bid getMinUtilityBid(){
        try{
            return utilitySpace.getMinUtilityBid();
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Makes a random offer above the minimum utility target
     * Accepts everything above the reservation value at the very end of the negotiation; or breaks off otherwise.
     */
    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions)
    {
        // Check for acceptance if we have received an offer
        //if we are the first to bid, then we generate a random bid
        //if we received and offer, we compare it to our reservation value
        if (lastOffer != null)
            if(TARGET_UTILITY > THRESHOLD){
                //Target utility concedes with every round
                System.out.println("TARGET UTILITY: " + TARGET_UTILITY);
                TARGET_UTILITY = TARGET_UTILITY*(1-getTimeLine().getTime());
            }
            if (timeline.getTime() >= 0.99)
                //this line is changed
                if (getUtility(lastOffer) >= utilitySpace.getReservationValue())
                    return new Accept(getPartyId(), lastOffer);
                else
                    return new EndNegotiation(getPartyId());
            else if(getUtility(lastOffer) >= TARGET_UTILITY)
                return new Accept(getPartyId(), lastOffer);
        // Otherwise, send out a random offer above the target utility
        return new Offer(getPartyId(), generateRandomBidAboveTarget());
    }

    private Bid generateRandomBidAboveTarget()
    {
        Bid randomBid;
        double util;
        int i = 0;
        // try 100 times to find a bid under the target utility
        do
        {
            randomBid = generateRandomBid();
            util = utilitySpace.getUtility(randomBid);
        }
        while (util < MINIMUM_TARGET && i++ < 100);
        return randomBid;
    }

    /**
     * Remembers the offers received by the opponent.
     */
    @Override
    public void receiveMessage(AgentID sender, Action action)
    {
        if (action instanceof Offer)
        {
            lastOffer = ((Offer) action).getBid();
        }
    }

    @Override
    public String getDescription()
    {
        return "Places random bids >= " + MINIMUM_TARGET;
    }

    /**
     * This stub can be expanded to deal with preference uncertainty in a more sophisticated way than the default behavior.
     */
    @Override
    public AbstractUtilitySpace estimateUtilitySpace()
    {
        return super.estimateUtilitySpace();
    }

}
