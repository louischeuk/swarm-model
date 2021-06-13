package models.trading;

import java.util.HashMap;
import models.trading.Trader.Type;
import simudyne.core.abm.AgentBasedModel;
import simudyne.core.abm.GlobalState;
import simudyne.core.abm.Group;
import simudyne.core.abm.Sequence;
import simudyne.core.abm.Split;
import simudyne.core.annotations.Constant;
import simudyne.core.annotations.Input;
import simudyne.core.annotations.ModelSettings;

@ModelSettings(macroStep = 100)
public class TradingModel extends AgentBasedModel<TradingModel.Globals> {

  @Constant(name = "Number of FT Traders")
  public int numFundamentalTrader = 80;

  @Constant(name = "Number of Momentum Traders")
  public int numMomentumTrader = 10;

  @Constant(name = "Number of Noise Traders")
  public int numNoiseTrader = 10;

  @Input(name = "Market price")
  public double marketPrice = 80.0;

  @Input(name = "Market True value") /* aka market equilibrium */
  public double trueValue = 100.0;


  public static final class Globals extends GlobalState {

    /* aka price elasticity. speed at which the market price converges market equilibrium */
    @Input(name = "Exchange's lambda")
    public double lambda = 0.3;

    @Input(name = "Standard deviation") /* for market true value */
    public double stdDev = 5;

    @Input(name = "Sensitivity to market")
    public double sensitivity = 0.005;     /*
                                              higher sensitivity, higher trade volumes
                                              tune this w.r.t. total amount of traders
                                              10  traders - 0.005
                                              20  traders - 0.005 to 0.015
                                              100 traders - 0.015 to 0.07
                                            */

    @Input(name = "Short selling duration")
    public int shortSellDuration = 200;

    @Input(name = "Max times of short in process")
    public int maxShortingInProcess = 200;

    /* smaller the value, more clusters formed */
    @Input(name = "vicinity Range")
    public double vicinityRange = 2; /* upon 1.5, all opinions converged */

    @Input(name = "Trust to average of other opinions")
    public double gamma = 0.001; /* speed of convergence of opinions */

    @Input(name = "influencer weighting") /* larger k, lower speed of convergence */
    public double k = 10;

    /* 0-1, larger number means a higher probability to trade in step */
    @Input(name = "Probability of momentum trade")
    public double probabilityMomentumTrade = 1;

    /* 0-1, larger number means a higher probability to trade in step */
    @Input(name = "Probability of noise trade")
    public double probabilityNoiseTrade = 1;

    @Input(name = "Opinion of influencer")
    public double influencerOpinion = 10;

  }

  /* ------------------- model initialisation -------------------*/
  @Override
  public void init() {
    createLongAccumulator("buys", "Number of buy orders");
    createLongAccumulator("sells", "Number of sell orders");
    createLongAccumulator("shorts", "Number of short sell orders");           /* inclusive */
    createLongAccumulator("coverShorts", "Number of short position covered"); /* inclusive */
    createDoubleAccumulator("price", "Market price");
    createDoubleAccumulator("opinions", "Opinions");

    registerAgentTypes(
        Market.class,
        FundamentalTrader.class, NoiseTrader.class, MomentumTrader.class,
        SocialNetwork.class, Influencer.class
    );

    registerLinkTypes(Links.TradeLink.class, Links.SocialNetworkLink.class);
  }

  @Override
  public void setup() {

    int numTrader = numFundamentalTrader + numMomentumTrader + numNoiseTrader;
    /* ---------------------- Groups creation ---------------------- */

    Group<Market> marketGroup = generateGroup(Market.class, 1,
        m -> {
          m.numTraders = numTrader;
          m.price = marketPrice;
          m.trueValue = trueValue;
          m.historicalPrices = new HashMap<>();
          m.historicalPrices.put(0L, m.price);
        });

    Group<FundamentalTrader> fundamentalTraderGroup = generateGroup(FundamentalTrader.class,
        numFundamentalTrader, t -> {
          /* parent class variable */
          t.type = Type.Fundamental;
          t.wealth = t.getPrng().exponential(100000000).sample();
//      t.shortDuration = t.getPrng().generator.nextInt(getGlobals().shortSellDuration) + 1;
          t.shortDuration = t.getGlobals().shortSellDuration;

          /* child class variables */
          t.intrinsicValue = t.getPrng().normal(trueValue, getGlobals().stdDev).sample();
          t.intrinsicNoOpnDynamics = t.intrinsicValue;
          t.opinion = t.getPrng().normal(0, 1).sample();
          t.zScore = t.opinion;
          System.out.println("Trader type: " + t.type);
        });

//        if (t.getID() < numTrader * 0.2) {
//          t.type = Type.Coordinated;
//          for (int i = 0; i < numTrader * 0.2; i++) { // group the WSB traders
//            t.addLink(i, Links.CoordinatedLink.class);
//          }

    if (numMomentumTrader > 0) {
      Group<MomentumTrader> momentumTraderGroup = generateGroup(MomentumTrader.class,
          numMomentumTrader, t -> {
            /* parent class variable */
            t.type = Type.Momentum;
            t.wealth = t.getPrng().exponential(100000000).sample();
            t.shortDuration = t.getGlobals().shortSellDuration;
            System.out.println("Trader type: " + t.type);
          });

      momentumTraderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
      marketGroup.fullyConnected(momentumTraderGroup, Links.TradeLink.class);
    }

    if (numNoiseTrader > 0) {
      Group<NoiseTrader> noiseTraderGroup = generateGroup(NoiseTrader.class, numNoiseTrader, t -> {
        t.wealth = t.getPrng().exponential(100000000).sample();
        t.shortDuration = t.getGlobals().shortSellDuration;
        t.type = Type.Noise;
        System.out.println("Trader type: " + t.type);
      });

      noiseTraderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
      marketGroup.fullyConnected(noiseTraderGroup, Links.TradeLink.class);
    }

    Group<SocialNetwork> socialMediaGroup = generateGroup(SocialNetwork.class, 1);

    Group<Influencer> influencerGroup = generateGroup(Influencer.class, 1,
        i -> {
          i.opinion = getGlobals().influencerOpinion; /* try 100 to have a nice uptrend of market price */
          i.probabilityToShare = 1;
          System.out.println("elon created");
        });

    /* ---------------------- connections ---------------------- */

    fundamentalTraderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
    marketGroup.fullyConnected(fundamentalTraderGroup, Links.TradeLink.class);

    fundamentalTraderGroup.fullyConnected(socialMediaGroup, Links.SocialNetworkLink.class);
    socialMediaGroup.fullyConnected(fundamentalTraderGroup, Links.SocialNetworkLink.class);

    influencerGroup.fullyConnected(socialMediaGroup, Links.SocialNetworkLink.class);

    super.setup();

  }

  @Override
  public void step() {
    super.step();

    Sequence subSequencePrice =
        Sequence.create(
            Market.sendPriceToTraders,
            Trader.processMarketPrice,
            Split.create(
                Market.calcPriceImpact,
                Market.updateTrueValue
            )
        );

    Sequence subSequenceOpinion =
        Sequence.create(
            Split.create(
                FundamentalTrader.shareOpinion,
                Influencer.shareOpinion
            ),
            SocialNetwork.publishOpinions,
            FundamentalTrader.fetchAndAdjustOpinion
        );

    run(
        Split.create(
            subSequencePrice,
            subSequenceOpinion
        ),
        Split.create(
            FundamentalTrader.adjustIntrinsicValue,
            MomentumTrader.calcMA
        )
    );

  }
}
