package models.trading;

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

  @Constant(name = "Number of Traders")
  public int numTrader = 10;

  @Input(name = "Proportion of FT traders")
  public double proportionFTTraders = 100;

  @Input(name = "Proportion of Noise traders")
  public double proportionNoiseTraders = 0;

  public static final class Globals extends GlobalState {

    @Input(name = "Market price")
    public double marketPrice = 80.0;

    @Input(name = "Real value of market price")
    public double trueValue = 100.0;

    /* aka price elasticity. speed at which the market price converges market equilibrium */
    @Input(name = "Exchange's lambda")
    public double lambda = 0.3;

    @Input(name = "Standard deviation") /* for market true value */
    public double stdDev = 5;

    @Input(name = "Short selling duration")
    public int shortSellDuration = 200;

    @Input(name = "Max times of short in process")
    public int maxShortingInProcess = 200;

    @Input(name = "Sensitivity to market")
    public double sensitivity = 0.005;     /*
                                              higher sensitivity, higher trade volumes
                                              tune this w.r.t. total amount of traders
                                              10  traders - 0.005
                                              20  traders - 0.005 to 0.015
                                              100 traders - 0.015 to 0.07
                                            */

    @Input(name = "Initial Margin Requirement")
    public double initialMarginRequirement = 0.5;

    @Input(name = "Maintenance Margin")
    public double maintenanceMargin = 0.3;

    /* smaller the value, more clusters formed */
    @Input(name = "vicinity Range")
    public double vicinityRange = 2; /* upon 1.5, all opinions converged */

    @Input(name = "Trust to average of other opinions")
    public double gamma = 0.001; /* speed of convergence of opinions */

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
    createDoubleAccumulator("marketTrueValue", "Market True Value");


    registerAgentTypes(
        Market.class,
        FundamentalTrader.class, NoiseTrader.class,
        SocialNetwork.class, Influencer.class);

    registerLinkTypes(Links.TradeLink.class, Links.SocialNetworkLink.class);
  }

  @Override
  public void setup() {

    /* proportion of traders */
    int numFundamentalTrader = (int) (numTrader * (proportionFTTraders / 100));
    int numNoiseTrader = (int) (numTrader * (proportionNoiseTraders / 100));

    /* ---------------------- Groups creation ---------------------- */

    Group<FundamentalTrader> fundamentalTraderGroup = generateGroup(FundamentalTrader.class,
        numFundamentalTrader, t -> {

          t.intrinsicValue = t.getPrng().normal(getGlobals().trueValue, getGlobals().stdDev)
              .sample();
          t.wealth = t.getPrng().exponential(100000000).sample();
//      t.shortDuration = t.getPrng().generator.nextInt(getGlobals().shortSellDuration) + 1;
          t.shortDuration = t.getGlobals().shortSellDuration;
          t.type = Type.Fundamental;
          t.opinion = t.getPrng().normal(0, 1).sample();
          t.zScore = t.opinion;
          t.intrinsicNoOpnDynamics = t.intrinsicValue;

          System.out.println("Trader type: " + t.type);

//        if (t.getID() < numTrader * 0.2) {
//          t.type = Type.Coordinated;
//          for (int i = 0; i < numTrader * 0.2; i++) { // group the WSB traders
//            t.addLink(i, Links.CoordinatedLink.class);
//          }
        });

//    Group<NoiseTrader> noiseTraderGroup = generateGroup(NoiseTrader.class, numNoiseTrader, t -> {
//      t.wealth = t.getPrng().exponential(100000000).sample();
////      t.shortDuration = t.getPrng().generator.nextInt(getGlobals().shortSellDuration) + 1;
//      t.shortDuration = t.getGlobals().shortSellDuration;
//      t.type = Type.Noise;
//
//      System.out.println("Trader type: " + t.type);
//    });

    Group<Market> marketGroup = generateGroup(Market.class, 1,
        market -> {
          market.numTraders = numTrader;
          market.price = getGlobals().marketPrice;
        });

    Group<SocialNetwork> socialMediaGroup = generateGroup(SocialNetwork.class, 1);

    Group<Influencer> influencerGroup = generateGroup(Influencer.class, 1,
        b -> {
          b.followers = numFundamentalTrader;
          b.opinion = 10; /* try 100 to have a nice uptrend of market price */
          b.probabilityToShare = 1;
        });

    /* ---------------------- connections ---------------------- */

    fundamentalTraderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
    marketGroup.fullyConnected(fundamentalTraderGroup, Links.TradeLink.class);

//    noiseTraderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
//    marketGroup.fullyConnected(noiseTraderGroup, Links.TradeLink.class);

    fundamentalTraderGroup.fullyConnected(socialMediaGroup, Links.SocialNetworkLink.class);
    socialMediaGroup.fullyConnected(fundamentalTraderGroup, Links.SocialNetworkLink.class);

    influencerGroup.fullyConnected(socialMediaGroup, Links.SocialNetworkLink.class);

    super.setup();

  }

  @Override
  public void step() {
    super.step();

    run(
        Split.create(
            Trader.processMarketPrice,
            FundamentalTrader.shareOpinion,
            Influencer.shareOpinion
        ),
        Split.create(
            Market.calcPriceImpact,
            Sequence.create(
                SocialNetwork.publishOpinions,
                FundamentalTrader.fetchAndAdjustOpinion
            ),
            Market.updateTrueValue
        ),
        FundamentalTrader.adjustIntrinsicValue
    );

  }
}
