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
  public int numTrader = 100;

  @Constant(name = "Real value of market price")
  public double realValue = 50.0;

  public static final class Globals extends GlobalState {

    @Input(name = "Market price")
    public double marketPrice = 50.0;

    // aka price elasticity. speed at which the market price converges market equilibrium
    @Input(name = "Exchange's lambda")
    public double lambda = 0.2;

    @Input(name = "Standard deviation") // for normal distribution
    public double stdDev = 10;

    @Input(name = "Short selling duration")
    public int shortSellDuration = 200;

    @Input(name = "Max times of short in process")
    public int maxShortingInProcess = 200;

    @Input(name = "Sensitivity to market")
    public double sensitivity = 0.04;     /*
                                              higher sensitivity, higher trade volumes
                                              tune this w.r.t. total amount of traders
                                              20  traders - 0.015
                                              100 traders - 0.015 to 0.07
                                            */

    @Input(name = "Initial Margin Requirement")
    public double initialMarginRequirement = 0.5;

    @Input(name = "Maintenance Margin")
    public double maintenanceMargin = 0.3;

    public boolean isMarketShockTriggered = false;

    @Input(name = "k to scale down the confidence factor")
    public double k = 2000;  /*
                               range from 500 - 10,000
                             */

    @Input(name = "Opinion multiple Factor")
    public double opinionFactor = 100;      /*
                                              tune it w.r.t to number of traders
                                              20  traders - 100
                                              100 traders - 500 to 1000
                                            */
  }

  /* ------------------- model initialisation -------------------*/
  @Override
  public void init() {
    createLongAccumulator("buys", "Number of buy orders");
    createLongAccumulator("sells", "Number of sell orders");
    createLongAccumulator("shorts", "Number of short sell orders"); // inclusive
    createLongAccumulator("coverShorts", "Number of short position covered"); // inclusive
    createDoubleAccumulator("price", "Market price");

    registerAgentTypes(
        Market.class,
        FundamentalTrader.class, NoiseTrader.class,
        SocialNetwork.class, Influencer.class);

    registerLinkTypes(Links.TradeLink.class, Links.SocialNetworkLink.class);
  }

  @Override
  public void setup() {

    // 80%: fundamental-trader | 20%: Noise-trader (haven't implemented anything yet)
    int numFundamentalTrader = (int) (numTrader * 0.8);
    int numNoiseTrader = (int) (numTrader * 0.2);

    /* ---------------------- Groups creation ---------------------- */

    Group<FundamentalTrader> fundamentalTraderGroup = generateGroup(FundamentalTrader.class,
        numFundamentalTrader, t -> {

          t.intrinsicValue = t.getPrng().normal(realValue, getGlobals().stdDev).sample();
          t.wealth = t.getPrng().exponential(100000000).sample();
//      t.shortDuration = t.getPrng().generator.nextInt(getGlobals().shortSellDuration) + 1;
          t.shortDuration = t.getGlobals().shortSellDuration;
          t.opinion = t.getPrng().uniform(-1, 1).sample();
          t.opinionThresh = t.getPrng().uniform(0, 1).sample();
          t.type = Type.Fundamental;

          System.out.println("Trader type: " + t.type);

//        if (t.getID() < numTrader * 0.2) {
//          t.type = Type.Coordinated;
//          for (int i = 0; i < numTrader * 0.2; i++) { // group the WSB traders
//            t.addLink(i, Links.CoordinatedLink.class);
//          }
        });

    Group<NoiseTrader> noiseTraderGroup = generateGroup(NoiseTrader.class, numNoiseTrader, t -> {
      t.wealth = t.getPrng().exponential(100000000).sample();
//      t.shortDuration = t.getPrng().generator.nextInt(getGlobals().shortSellDuration) + 1;
      t.shortDuration = t.getGlobals().shortSellDuration;
      t.type = Type.Noise;

      System.out.println("Trader type: " + t.type);
    });

    Group<Market> marketGroup = generateGroup(Market.class, 1,
        market -> {
          market.numTraders = numTrader;
          market.price = getGlobals().marketPrice;
        });

    Group<SocialNetwork> socialMediaGroup = generateGroup(SocialNetwork.class, 1);

    Group<Influencer> influencerGroup = generateGroup(Influencer.class, 1,
        b -> {
          b.followers = numFundamentalTrader;
          b.opinion = 1.0;
          b.probabilityToShare = 0.4;
        });

    /* ---------------------- connections ---------------------- */

    fundamentalTraderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
    marketGroup.fullyConnected(fundamentalTraderGroup, Links.TradeLink.class);

    noiseTraderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
    marketGroup.fullyConnected(noiseTraderGroup, Links.TradeLink.class);

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
            )
        ),
        Split.create(
            FundamentalTrader.adjustIntrinsicValue,
            FundamentalTrader.updateOpinionThreshold
        )
    );

  }
}
