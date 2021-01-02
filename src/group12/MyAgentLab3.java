package group12;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.EndNegotiation;
import genius.core.actions.Offer;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;

import java.util.HashMap;
import java.util.List;

/**
 * A simple example agent that makes random bids above a minimum target utility.
 *
 * @author Tim Baarslag
 */
public class MyAgentLab3 extends AbstractNegotiationParty
{
    private static double MINIMUM_TARGET = 0.8;
    private static double TARGET_UTILITY;
    private static double THRESHOLD;
    private Bid lastOffer;
    private int[][] table;
    private AdditiveUtilitySpace additiveUtilitySpace;
    private HashMap<String,Integer> indicies;

    /**
     * Initializes a new instance of the agent.
     */
    @Override
    public void init(NegotiationInfo info)
    {
        super.init(info);
        AbstractUtilitySpace utilitySpace = info.getUtilitySpace();

        //UtilitySpace: domain and preference profile
        additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;

        //List of issues
        List<Issue> issues = additiveUtilitySpace.getDomain().getIssues();

        table = new int[issues.size()][];
        indicies = new HashMap<>();

        for (Issue issue : issues) {
            int issueNumber = issue.getNumber();
            //System.out.println(">> " + issue.getName() + " weight: " + additiveUtilitySpace.getWeight(issueNumber));
            //System.out.println("issue number: " + issueNumber + " issue name: " + issue.getName());

            // Assuming that issues are discrete only
            IssueDiscrete issueDiscrete = (IssueDiscrete) issue;

            //thats the evaluation function for all the values of the issue (the nominator in the fraction used to
            //calculate the utility of the agent, thats the function evalj(oi) bit from sumn(wji(evalj(oi)/max(evalj(Ii))))
            EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) additiveUtilitySpace.getEvaluator(issueNumber);

            int counter = 0;
            for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
                //System.out.println(valueDiscrete.getValue());
                String s = issueNumber + valueDiscrete.getValue();
                indicies.put(s,counter);
                counter++;

                //This gives the number of the value, no need to keep a HashMap!
                //System.out.println("Evaluation(getValue): " + evaluatorDiscrete.getValue(valueDiscrete));
                try {
                    //This gives the evaluation of the value
                    //System.out.println("Evaluation(getEvaluation): " + evaluatorDiscrete.getEvaluation(valueDiscrete));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            //Gotta be caureful -> issues start at 1, array starts at 0!!!
            table[issueNumber-1] = new int[issueDiscrete.getNumberOfValues()];
        }



        TARGET_UTILITY = getUtility(getMaxUtilityBid());
        THRESHOLD = (getUtility(getMaxUtilityBid())+getUtility(getMinUtilityBid()))/2;
        //System.out.println("THRESHOLD: " + THRESHOLD);
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
                //System.out.println("TARGET UTILITY: " + TARGET_UTILITY);
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

            for(Issue issue : lastOffer.getIssues()){
                Value value = lastOffer.getValue(issue);
                //EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) additiveUtilitySpace.getEvaluator(issue.getNumber());

                int issueIndex = issue.getNumber()-1;
                //int valueIndex = evaluatorDiscrete.getValue((ValueDiscrete)value);

                int valueIndex = indicies.get(issue.getNumber() + ((ValueDiscrete) value).getValue());

                table[issueIndex][valueIndex]++;
                //System.out.println("incremented table[" + issueIndex + "," + valueIndex+"] for isssue: " + issue.getName() + " value: " + ((ValueDiscrete) value).getValue());
            }

            //Once we incremented the table, we calculate utility estimate
            System.out.println("Opponent Utility Estimate: " + calcOppUtil(lastOffer));
        }
    }

    public double getVo(Issue issue, int valueIndex){
        IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
        int[] iss = table[issue.getNumber()-1];
        int rank = 0;

        for(int i=0; i<iss.length; i++){
            if(i != valueIndex){
                if(iss[i] < iss[valueIndex]){
                    rank++;
                } else if(iss[i] == iss[valueIndex]){
                    //check lexographical order
                    Value vi = issueDiscrete.getValue(i);
                    Value vv = issueDiscrete.getValue(valueIndex);
                    if(vv.toString().toLowerCase().compareTo(vi.toString().toLowerCase()) > 0){
                        rank++;
                    }
                }
            }
        }

        double k = (double) issueDiscrete.getNumberOfValues();
        double v0 = (k-rank+1)/k;
        System.out.println("Vo: " + v0);
        return v0;
    }

    public double[] getDoubleUs(){
        int noIssues = utilitySpace.getDomain().getIssues().size();
        double[] doubleUsHat = new double[noIssues];
        for(int i=0; i<noIssues; i++){
            int[] iss = table[i];
            double sumSquare = 0;
            for(int j : iss){
                sumSquare += j*j;
            }
            doubleUsHat[i] = sumSquare;
        }

        double[] doubleUs = new double[noIssues];
        double sum = 0;
        for(int i=0; i<noIssues; i++){
            sum += doubleUsHat[i];
        }

        for(int i=0; i<noIssues; i++){
            doubleUs[i] = doubleUsHat[i]/sum;
        }

        return doubleUs;
    }

    public double calcOppUtil(Bid bid){
        double[] doubleUs = getDoubleUs();

        double oppUtil = 0;
        int iss = 0;
        for(Issue issue : bid.getIssues()){
            Value value = lastOffer.getValue(issue);
            EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) additiveUtilitySpace.getEvaluator(issue.getNumber());
            int valueIndex = evaluatorDiscrete.getValue((ValueDiscrete)value);
            double v0 = getVo(issue,valueIndex);
            double weight = doubleUs[iss];

            oppUtil += weight*v0;
        }

        return oppUtil;
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
