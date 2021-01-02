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
import genius.core.uncertainty.BidRanking;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;
import org.w3c.dom.ls.LSOutput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A simple example agent that makes random bids above a minimum target utility.
 *
 * @author Tim Baarslag
 */
public class Lab3 extends AbstractNegotiationParty
{
    private static double MINIMUM_TARGET = 0.8;
    private static double TARGET_UTILITY;
    private static double THRESHOLD;
    private Bid lastOffer;
    int[][] table;
    HashMap<String,Integer> indicies;
    int numberOfBids;
    private static int N = 10;
    private Bid lowest;
    private Bid highest;
    private ArrayList<Uij> uoos;

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
        table = new int[issues.size()][];
        indicies = new HashMap<>();
        numberOfBids = 0;
        uoos = new ArrayList<>();

        for (Issue issue : issues) {
            int issueNumber = issue.getNumber();
            //System.out.println(">> " + issue.getName() + " weight: " + additiveUtilitySpace.getWeight(issueNumber));

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

            //System.out.println("---------------------------------");

            int issueIndex = issueNumber-1;
            table[issueIndex] = new int[issueDiscrete.getValues().size()];
            //System.out.println("ISSUE: " + issueNumber + " SIZE: "+ issueDiscrete.getValues().size());
        }

        TARGET_UTILITY = getUtility(getMaxUtilityBid());
        THRESHOLD = (getUtility(getMaxUtilityBid())+getUtility(getMinUtilityBid()))/2;
        System.out.println("THRESHOLD: " + THRESHOLD);

        BidRanking bidRanking = userModel.getBidRanking();
        lowest = bidRanking.getBidOrder().get(0);
        highest = bidRanking.getBidOrder().get(bidRanking.getBidOrder().size()-1);

        /*
        U(lowest) >= 0
        U(highest) <= 1
         */
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
        ArrayList<Bid> bids = new ArrayList<>();
        Bid randomBid = generateRandomBid();
        double util;
        int i = 0;
        // try 100 times to find a bid under the target utility
        for(int j=0; j<N; j++){
            do
            {
                randomBid = generateRandomBid();
                util = utilitySpace.getUtility(randomBid);
            }
            while (util < MINIMUM_TARGET && i++ < 100);
            bids.add(randomBid);
        }

        Bid highestBid = randomBid;
        double oppUtil = 0;
        for(Bid bid : bids){
            if(getOppUtil(bid) > oppUtil){
                oppUtil = getOppUtil(bid);
                highestBid = bid;
            }
        }

        return highestBid;
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
            numberOfBids++;


            String bid = "Bid " + numberOfBids + " = ";
            for(Issue issue : lastOffer.getIssues()){
                ValueDiscrete valueDiscrete = (ValueDiscrete) lastOffer.getValue(issue);
                bid += valueDiscrete.getValue() + ", ";
                String s = issue.getNumber() + valueDiscrete.getValue();
                table[issue.getNumber()-1][indicies.get(s)] = table[issue.getNumber()-1][indicies.get(s)] + 1;
            }

            //System.out.println(bid);

            for(Issue issue : lastOffer.getIssues()){
                //System.out.print("ISSUE: " + issue.getNumber());
                //printArr(table[issue.getNumber()-1]);
            }

            getOppUtil(lastOffer);
        }
    }

    public void printArr(int[] arr){
        System.out.print("[");
        for(int i=0; i<arr.length-1; i++){
            System.out.print(arr[i] + ",");
        }
        System.out.print(arr[arr.length-1]);
        System.out.println("]");
    }

    public double getOppUtil(Bid lastOffer){
        double oppUtil = 0;
        for(Issue issue : lastOffer.getIssues()){
            double v0 = getVo(issue, (ValueDiscrete) lastOffer.getValue(issue));
            double wi = getDoubleU(issue.getNumber());
            oppUtil += wi*v0;
            //System.out.println("Vo: " + v0 + " wi " + wi);
        }

        System.out.println("OppUtil:  " + oppUtil);
        return oppUtil;
    }

    public double getDoubleUHat(int issueNumber){
        int issueIndex = issueNumber - 1;
        int[] frequencies = table[issueIndex];

        double what = 0;
        for(int frequency : frequencies){
            double freq = (double) frequency;
            double time = (double) numberOfBids-1;

            what += (freq*freq)/(time*time);
        }

        return what;
    }

    public double getDoubleU(int issueNumber){
        double sum = 0;
        for(Issue issue : utilitySpace.getDomain().getIssues()){
            double whatj = getDoubleUHat(issue.getNumber());
            sum += whatj;
        }

        double whati = getDoubleUHat(issueNumber);
        double wi = whati/sum;
        return wi;
    }

    public double getVo(Issue issue, ValueDiscrete valueDiscrete){
        int[] optionFrequencies = table[issue.getNumber()-1];
        String s = issue.getNumber() + valueDiscrete.getValue();
        int valueIndex = indicies.get(s);
        IssueDiscrete issueDiscrete = (IssueDiscrete) issue;

        int rank = 0;
        for(int i=0; i<optionFrequencies.length; i++){
            if(i!=valueIndex){
                if(optionFrequencies[i] < optionFrequencies[valueIndex]){
                    rank++;
                }
                if(optionFrequencies[i] == optionFrequencies[valueIndex]){
                    Value vi = issueDiscrete.getValue(i);
                    Value vv = issueDiscrete.getValue(valueIndex);
                    if(vv.toString().toLowerCase().compareTo(vi.toString().toLowerCase()) > 0){
                        rank++;
                    }
                }
            }
        }

        int k = optionFrequencies.length;
        rank = k - rank;

        double kk = (double) k;
        double rankr = (double) rank;
        double v0 = (kk-rankr+1)/k;
        //System.out.println("Vo of " + valueDiscrete.getValue() + " is: " + v0);
        return v0;
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

    private class Uij {
        //issue number
        private int i;
        //issue option
        private int j;

        public Uij(int i, int j){
            this.i = i;
            this.j = j;
        }

        public Uij(int i, String value){
            this.i = i;
            this.j = indicies.get(i + value);
        }

        public int getI(){ return i; }
        public int getJ(){ return j; }
    }

}
