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

  @Constant(name = "Number of FT Traders")
  public int numFundamentalTrader = 10;

  @Constant(name = "Number of Noise Traders")
  public int numNoiseTrader = 0;

  @Constant(name = "Number of Momentum Traders")
  public int numMomentumTrader = 10;

  @Constant(name = "Number of Coordinated Traders")
  public int numCoordinatedTrader = 0;

  @Constant(name = "Number of influencers")
  public int numInfluencer = 0;

  @Input(name = "Market price")
  public float marketPrice = 80.0F;

  @Input(name = "Market True value") /* aka market equilibrium */
  public double trueValue = 120.0;

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

    @Input(name = "Trust to average of other opinions") /* speed of convergence of opinions */
    public double gamma = 0.001;

    @Input(name = "influencer weighting") /* larger k, lower speed of convergence */
    public double k = 10;

    @Input(name = "Opinion of influencer")
    public double influencerOpinion = 3;

    @Input(name = "Opinion of Coordinated T")
    public double coordinatedTraderOpinion = 3;

    /* 0-1, larger number means a higher probability to trade in step */
    @Input(name = "Probability of noise trade")
    public double probabilityNoiseTrade = 1;

    /* 0-1, larger number means a higher probability to trade in step */
    @Input(name = "Probability of coordinated trade")
    public double probabilityCoordinatedTrade = 1;

  }

  /* ------------------- model initialisation -------------------*/
  @Override
  public void init() {
    createLongAccumulator("buys", "Number of buy orders");
    createLongAccumulator("sells", "Number of sell orders");
    createLongAccumulator("shorts", "Number of short sell orders");           /* inclusive */
    createLongAccumulator("closeShorts", "Number of short position covered"); /* inclusive */
    createDoubleAccumulator("price", "Market price");
    createDoubleAccumulator("opinions", "Opinions");

    registerAgentTypes(
        Market.class, DataProvider.class, SocialNetwork.class, Influencer.class,
        FundamentalTrader.class, NoiseTrader.class, MomentumTrader.class, CoordinatedTrader.class
    );

    registerLinkTypes(Links.TradeLink.class,
        Links.SocialNetworkLink.class,
        Links.DataProviderLink.class);
  }

  @Override
  public void setup() {

    /* ---------------------- Groups creation ---------------------- */

    Group<SocialNetwork> socialMediaGroup = generateGroup(SocialNetwork.class, 1);

    Group<DataProvider> dataProviderGroup = generateGroup(DataProvider.class, 1,
        d -> d.trueValue = trueValue);

    Group<Market> marketGroup = generateGroup(Market.class, 1, m -> m.price = marketPrice);

    marketGroup.fullyConnected(dataProviderGroup, Links.DataProviderLink.class);

    if (numFundamentalTrader > 0) {
      Group<FundamentalTrader> fundamentalTraderGroup = generateGroup(FundamentalTrader.class,
          numFundamentalTrader, t -> {
            t.type = Type.Fundamental;
            t.wealth = t.getPrng().exponential(100000000).sample();
//      t.shortDuration = t.getPrng().generator.nextInt(getGlobals().shortSellDuration) + 1;
            t.shortDuration = t.getGlobals().shortSellDuration;

            t.intrinsicValue = t.getPrng().normal(trueValue, getGlobals().stdDev).sample();
            t.zScore = t.getPrng().normal(0, 1).sample();
            System.out.println("Trader type: " + t.type);
          });

      fundamentalTraderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
      marketGroup.fullyConnected(fundamentalTraderGroup, Links.TradeLink.class);

      dataProviderGroup.fullyConnected(fundamentalTraderGroup, Links.DataProviderLink.class);
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

    if (numMomentumTrader > 0) {
      Group<MomentumTrader> momentumTraderGroup = generateGroup(MomentumTrader.class,
          numMomentumTrader, t -> {
            t.wealth = t.getPrng().exponential(100000000).sample();
            t.shortDuration = t.getGlobals().shortSellDuration;

            t.opinion = t.getPrng().normal(0, 1).sample();
            t.type = Type.Momentum;
            System.out.println("Trader type: " + t.type);
          });

      momentumTraderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
      marketGroup.fullyConnected(momentumTraderGroup, Links.TradeLink.class);

      momentumTraderGroup.fullyConnected(socialMediaGroup, Links.SocialNetworkLink.class);
      socialMediaGroup.fullyConnected(momentumTraderGroup, Links.SocialNetworkLink.class);
    }

    if (numCoordinatedTrader > 0) {
      Group<CoordinatedTrader> coordinatedTraderGroup = generateGroup(CoordinatedTrader.class,
          numCoordinatedTrader, t -> {
            t.wealth = t.getPrng().exponential(100000000).sample();
            t.shortDuration = t.getGlobals().shortSellDuration;
            t.type = Type.Coordinated;
            t.opinion = getGlobals().coordinatedTraderOpinion;
            System.out.println("Trader type: " + t.type);
          });

      coordinatedTraderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
      marketGroup.fullyConnected(coordinatedTraderGroup, Links.TradeLink.class);

      coordinatedTraderGroup.fullyConnected(socialMediaGroup, Links.SocialNetworkLink.class);
      socialMediaGroup.fullyConnected(coordinatedTraderGroup, Links.SocialNetworkLink.class);
    }

    if (numInfluencer > 0) {
      Group<Influencer> influencerGroup = generateGroup(Influencer.class, 1,
          i -> {
            i.opinion = getGlobals().influencerOpinion; /* try 100 to have a nice uptrend of market price */
            i.probabilityToShare = 1;
            System.out.println("elon created");
          });

      influencerGroup.fullyConnected(socialMediaGroup, Links.SocialNetworkLink.class);
    }

    super.setup();
  }

  @Override
  public void step() {
    super.step();

    Sequence subSequencePrice =
        Sequence.create(
            Market.sendPriceToTraders,
            Split.create(
                MomentumTrader.updateMomentum,
                Trader.submitLimitOrders
            ),
            Market.calcPriceImpact,
            DataProvider.updateTrueValue
        );

    Sequence subSequenceOpinion =
        Sequence.create(
            Split.create(
                MomentumTrader.shareOpinion,
                CoordinatedTrader.shareOpinion
            ),
            SocialNetwork.publishOpinions,
            MomentumTrader.fetchAndAdjustOpinion
        );

    run(
        Sequence.create(
            Split.create(
                subSequencePrice,
                subSequenceOpinion
            ),
            Split.create(
                FundamentalTrader.adjustIntrinsicValue
            )
        )

    );

  }
}
