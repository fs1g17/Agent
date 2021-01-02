package group12;

import java.util.*;

import bargainingchips.actions.Breakoff;
import genius.core.AgentID;
import genius.core.Bid;
import genius.core.Domain;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.EndNegotiation;
import genius.core.actions.Offer;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.OutcomeSpace;
import genius.core.boaframework.SortedOutcomeSpace;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.ExperimentalUserModel;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;
import genius.core.utility.UncertainAdditiveUtilitySpace;
import gurobi.*;

/**
 * ExampleAgent returns the bid that maximizes its own utility for half of the negotiation session.
 * In the second half, it offers a random bid. It only accepts the bid on the table in this phase,
 * if the utility of the bid is higher than Example Agent's last bid.
 */
public class ExampleAgent extends AbstractNegotiationParty {
    private final String description = "Example Agent";

    private Bid lastReceivedOffer; // offer on the table
    private Bid myLastOffer;
    Domain domain;
    double reservationValue;
    double aspirationValue = 0.5;
    Set<Bid> unknownBids;
    private static double MINIMUM_TARGET = 0.8;
    private static double TARGET_UTILITY;
    private static double THRESHOLD;
    private Bid lastOffer;
    int[][] table;
    HashMap<String,Integer> indicies;
    int numberOfBids;
    private HashMap<String, GRBVar> vars;
    private HashMap<String, Double> vals;
    private AdditiveUtilitySpace additiveUtilitySpace;
    private int mseCounter =0;
    private double mseSum = 0;

    UncertainAdditiveUtilitySpace realUSpace;
    AbstractUtilitySpace abstractUtilitySpace;


    @Override
    public void init(NegotiationInfo info) {
        super.init(info);

        domain = userModel.getDomain();
        reservationValue = info.getUtilitySpace().getReservationValue();

        AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
        OutcomeSpace outcomeSpace = new OutcomeSpace(utilitySpace);

        //Gets all bids without a utility
        unknownBids = new HashSet<>(outcomeSpace.getAllBidsWithoutUtilities());

        //Removes all bids that are in the preference order from unknown bids
        for (Bid bidRank : userModel.getBidRanking().getBidOrder())
        {
            unknownBids.remove(bidRank);
        }

        //Use this for evaluation as it provides real utility
        ExperimentalUserModel e = (ExperimentalUserModel) userModel ;
        realUSpace = e.getRealUtilitySpace();

        //UtilitySpace: domain and preference profile
        additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;

        //List of issues
        List<Issue> issues = additiveUtilitySpace.getDomain().getIssues();
        table = new int[issues.size()][];
        indicies = new HashMap<>();
        numberOfBids = 0;


        for (Issue issue : issues) {
            int issueNumber = issue.getNumber();
            IssueDiscrete issueDiscrete = (IssueDiscrete) issue;

            int counter = 0;
            for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
                String s = issueNumber + valueDiscrete.getValue();
                indicies.put(s,counter);
                counter++;
            }

            int issueIndex = issueNumber-1;
            table[issueIndex] = new int[issueDiscrete.getValues().size()];
        }

        updateModel();


    }

    public double getOppUtil(Bid lastOffer){
        double oppUtil = 0;
        for(Issue issue : lastOffer.getIssues()){
            double v0 = getVo(issue, (ValueDiscrete) lastOffer.getValue(issue));
            double wi = getDoubleU(issue.getNumber());
            oppUtil += wi*v0;
            //System.out.println("Vo: " + v0 + " wi " + wi);
        }

        return oppUtil;
    }


    private double getBidUtility(Bid bid){
        double sum = 0.0;
        for(Issue issue : bid.getIssues()){
            ValueDiscrete valueDiscrete = (ValueDiscrete) bid.getValue(issue);
            sum += vals.get(issue.getNumber() + valueDiscrete.getValue());
        }
        return sum;
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

    private void updateModel(){
        List<Bid> bidList = userModel.getBidRanking().getBidOrder();
        List<Issue> issues = additiveUtilitySpace.getDomain().getIssues();
        try{
            GRBEnv env = new GRBEnv(true);
            env.set("logFile", "LP2.log");
            env.set(GRB.IntParam.OutputFlag, 0);
            env.start();
            // Create empty model
            GRBModel model = new GRBModel(env);

            //firstf -> create all vars
            vars = new HashMap<>();
            vals = new HashMap<>();
            for(Issue issue : issues){
                IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
                for(ValueDiscrete valueDiscrete : issueDiscrete.getValues()){
                    String s = issue.getNumber() + valueDiscrete.getValue();
                    GRBVar a = model.addVar(0.0,1.0,0.0, GRB.CONTINUOUS, s);
                    vars.put(s,a);
                }
            }

            GRBVar epsilon = model.addVar(0.0,10,0.0,GRB.CONTINUOUS,"epsilon");

            for(int i=0; i<bidList.size()-1; i++){
                //b1 <= b2
                Bid b1 = bidList.get(i);
                Bid b2 = bidList.get(i+1);

                GRBLinExpr LHS = new GRBLinExpr();
                GRBLinExpr RHS = new GRBLinExpr();
                LHS.addTerm(1.0,epsilon);

                for(Issue issue : issues){
                    ValueDiscrete v1 = (ValueDiscrete) b1.getValue(issue);
                    ValueDiscrete v2 = (ValueDiscrete) b2.getValue(issue);

                    String s1 = issue.getNumber() + v1.getValue();
                    String s2 = issue.getNumber() + v2.getValue();

                    LHS.addTerm(1.0,vars.get(s1));
                    RHS.addTerm(1.0,vars.get(s2));
                }

                model.addConstr(LHS,GRB.LESS_EQUAL,RHS,i+"<=");
            }

            Bid lowest = bidList.get(0);
            Bid highest = bidList.get(bidList.size()-1);

            GRBLinExpr lowestHS = new GRBLinExpr();
            GRBLinExpr highestHS = new GRBLinExpr();

            for(Issue issue : issues){
                ValueDiscrete v1 = (ValueDiscrete) lowest.getValue(issue);
                ValueDiscrete v2 = (ValueDiscrete) highest.getValue(issue);

                String s1 = issue.getNumber() + v1.getValue();
                String s2 = issue.getNumber() + v2.getValue();

                lowestHS.addTerm(1.0,vars.get(s1));
                highestHS.addTerm(1.0,vars.get(s2));
            }

            model.addConstr(lowestHS, GRB.GREATER_EQUAL, 0.0, "min");
            model.addConstr(highestHS, GRB.LESS_EQUAL, 1.0, "max");

            GRBLinExpr objective = new GRBLinExpr();
            objective.addTerm(1.0,epsilon);
            model.setObjective(objective,GRB.MAXIMIZE);

            model.optimize();
            GRBVar[] vars = model.getVars();

            for(int i=0; i<vars.length; i++){
                GRBVar a = vars[i];
                //System.out.println(a.get(GRB.StringAttr.VarName) + " " + a.get(GRB.DoubleAttr.X));
                vals.put(a.get(GRB.StringAttr.VarName), a.get(GRB.DoubleAttr.X));
            }

            model.dispose();
            env.dispose();
        } catch (GRBException ee) {
            ee.printStackTrace();
            System.out.println("FAILLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL");
        }
    }


    /**
     * When this function is called, it is expected that the Party chooses one of the actions from the possible
     * action list and returns an instance of the chosen action.
     *
     * @param list
     * @return
     */
    @Override
    public Action chooseAction(List<Class<? extends Action>> list) {
        // According to Stacked Alternating Offers Protocol list includes
        // Accept, Offer and EndNegotiation actions only.
        double time = getTimeLine().getTime(); // Gets the time, running from t = 0 (start) to t = 1 (deadline).
        // The time is normalized, so agents need not be
        // concerned with the actual internal clock.


        aspirationValue *= 0.9;
        double maxNegotiationVal = -1;

        /**
         * Update User Model
         */

        //Estimate opponent utility for each offer

        /**
         * Call elicitation strategy to get a partial preference
         */

        elicitationStrategy();

        List<Bid> bidOrder = userModel.getBidRanking().getBidOrder();


        Bid offer = null;
        //double opponentUtility = Math.random();
        double opponentUtility = 1.0;

        if(lastOffer != null){
            opponentUtility = getOppUtil(lastOffer);
        } else {
            System.out.println("LASTOFFER was null for some reason hm...........");
        }

        for (Bid bid : bidOrder)
        {
            double negotationVal = ((opponentUtility * getBidUtility(bid)) + ((1-opponentUtility) * aspirationValue));

            if (negotationVal > maxNegotiationVal)
            {
                maxNegotiationVal = negotationVal;
                offer = bid;
            }
        }

        if (maxNegotiationVal == reservationValue)
        {
            return new EndNegotiation(this.getPartyId());
        }
        else if (lastReceivedOffer != null && lastReceivedOffer.equals(offer))
        {
            return new Accept(this.getPartyId(), lastReceivedOffer);
        }
        else
        {
            return new Offer(this.getPartyId(), offer);
        }
    }

    public void elicitationStrategy()
    {
        //Map containing bid and "z-index"
        Map<Bid, Double> zMap = new HashMap<>();
        double opponentUtility = Math.random();

        //Calculates z-index for each unknown bid
        for (Bid bid : unknownBids)
        {
            double z = ((opponentUtility * getBidUtility(bid)) + ((1-opponentUtility) * aspirationValue)) - user.getElicitationCost();
            zMap.put(bid, z);
        }

        //Creates a maxHeap containing all z-index's
        PriorityQueue<Bid> maxHeap = new PriorityQueue<>((a,b) -> Double.compare(zMap.get(b), zMap.get(a)));
        maxHeap.addAll(zMap.keySet());

        double z = getBidUtility(maxHeap.peek());

        List<Bid> bidOrder = userModel.getBidRanking().getBidOrder();

        double v = 0;

        //Gets current maximum known negotiation value out of the known bids
        for (Bid bid : bidOrder)
        {
            v = Math.max(v, ((opponentUtility * getBidUtility(bid)) + ((1-opponentUtility) * aspirationValue)));
        }

        //Elicit bids which have a estimated utility value higher than our current maximum known value
        while (!maxHeap.isEmpty() && z >= v)
        {
            Bid w = maxHeap.poll();

            //  System.out.println("Old value: " + (zMap.get(newBid) + user.getElicitationCost()));
            // System.out.println("Real Utility: " + realUSpace.getUtility(w));
            double estimatedUtility = getBidUtility(w);
            System.out.println("");
            System.out.println("Bid: " + mseCounter);
            System.out.println("Predicted utility: " + estimatedUtility);
            double realUtility = realUSpace.getUtility(w);
            userModel = user.elicitRank(w, userModel);
            updateModel();
            //System.out.println("Elicited prediction value: " + getBidUtility(w));

            double accuracy = (Math.abs(realUtility - estimatedUtility)/realUtility) * 100;
            mseSum += accuracy;

            System.out.println("Accuracy " + mseCounter + ": " + mseSum/mseCounter);

            mseCounter++;

            unknownBids.remove(w);
            v = Math.max(v, ((opponentUtility * getUtility(w)) + ((1-opponentUtility) * aspirationValue)));
            z = maxHeap.isEmpty() ? 0 : getUtility(maxHeap.peek());
        }
    }


    /**
     * This method is called to inform the party that another NegotiationParty chose an Action.
     * @param sender
     * @param act
     */
    @Override
    public void receiveMessage(AgentID sender, Action act) {
        super.receiveMessage(sender, act);

        if (act instanceof Offer) { // sender is making an offer
            Offer offer = (Offer) act;

            // storing last received offer
            lastReceivedOffer = offer.getBid();
        }
    }

    /**
     * A human-readable description for this party.
     * @return
     */
    @Override
    public String getDescription() {
        return description;
    }

    private Bid getMaxUtilityBid() {
        try {
            return this.utilitySpace.getMaxUtilityBid();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
