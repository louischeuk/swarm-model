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

@ModelSettings(macroStep = 200)
public class TradingModel extends AgentBasedModel<TradingModel.Globals> {

  @Input(name = "Market price")
  public float marketPrice = 5.0F;

  @Input(name = "Market equilibrium")
  public double trueValue = 5.0; /* called v_0 in paper */

  public static final class Globals extends GlobalState {

    @Constant(name = "Number of FT Traders")
    public int numFundamentalTrader = 100;

    @Constant(name = "Number of Noise Traders")
    public int numNoiseTrader = 0;

    @Constant(name = "Number of Momentum Traders")
    public int numMomentumTrader = 100;

    @Constant(name = "Number of Coordinated Traders")
    public int numCoordinatedTrader = 0;

    @Constant(name = "Number of influencers")
    public int numInfluencer = 0;

    /* aka price elasticity. speed at which the market price converges market equilibrium */
    @Input(name = "Exchange's lambda")
    public double lambda = 1; // 0.3?, 0.025

    @Input(name = "Uncertainty of true value")
    public double sigma_u = 0;

    /* for market true value */
    @Input(name = "Standard deviation of random walk")
    public double sigma_v = 0;

    @Input(name = "Std. dev. of Jump Diffusion")
    public double sigma_jd = 2; // 2? 3?

    @Input(name = "Jump Diffusion's Lambda")
    public double lambda_jd = 5; // 1? 3?

    @Input(name = "Demand of noise traders")
    public double sigma_n = 0.15; // 1? 3?

    @Input(name = "Sensitivity to market")
    public double ftParam_kappa = 0.08; // aka. sensitivity (0.5 before)

    @Input(name = "MT Params: alpha")
    public double mtParams_alpha = 0.2; // or 0.8, 0.9 or 0.5 (to be safe)

    @Input(name = "MT Params: Beta")
    public double mtParams_beta = 0.1; // 10 before

    @Input(name = "MT Params: gamma")
    public double mtParams_gamma = 50;

    @Input(name = "Short selling duration")
    public int shortSellDuration = 200;

    @Input(name = "Max times of short in process")
    public int maxShortingInProcess = 200;

    /* smaller the value, more clusters formed */
    @Input(name = "vicinity Range")
    public double vicinityRange = 3; /*
                                        2: converge to close to 0
                                        above 2: all converge to 3
                                     */

    /* speed of convergence of opinions */
    @Input(name = "Trust to average of other opinions")
    public double gamma = 0.025;

    /* larger k, lower speed of convergence */
    @Input(name = "influencer weighting")
    public double k = 10;

    @Input(name = "Opinion of influencer")
    public double influencerOpinion = 3;

    @Input(name = "Opinion of Coordinated T")
    public double ctOpinion = 3;

    /* 0-1, larger number means a higher probability to trade in step */
    @Input(name = "Probability of noise trade")
    public double pNoiseTrade = 1;

    /* 0-1, larger number means a higher probability to trade in step */
    @Input(name = "Probability of coordinated trade")
    public double pCoordinatedTrade = 1;
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
    createDoubleAccumulator("equilibrium", "Market equilibrium");
    createDoubleAccumulator("MtDemand", "Momentum getDemand()");

    registerAgentTypes(
        Exchange.class, DataProvider.class, SocialNetwork.class, Influencer.class,
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

    Group<Exchange> marketGroup = generateGroup(Exchange.class, 1, m -> m.price = marketPrice);

    marketGroup.fullyConnected(dataProviderGroup, Links.DataProviderLink.class);

    if (getGlobals().numFundamentalTrader > 0) {
      Group<FundamentalTrader> fundamentalTraderGroup = generateGroup(FundamentalTrader.class,
          getGlobals().numFundamentalTrader, t -> {
            t.type = Type.Fundamental;
            t.wealth = t.getPrng().exponential(100000000).sample();
//      t.shortDuration = t.getPrng().generator.nextInt(getGlobals().shortSellDuration) + 1;
            t.shortDuration = t.getGlobals().shortSellDuration;
            t.zScore = t.getPrng().normal(0, 1).sample();
            t.intrinsicValue = trueValue + t.zScore * getGlobals().sigma_u;

            System.out.println("Trader type: " + t.type);
          });

      fundamentalTraderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
      marketGroup.fullyConnected(fundamentalTraderGroup, Links.TradeLink.class);

      dataProviderGroup.fullyConnected(fundamentalTraderGroup, Links.DataProviderLink.class);
    }

    if (getGlobals().numNoiseTrader > 0) {
      Group<NoiseTrader> noiseTraderGroup = generateGroup(NoiseTrader.class,
          getGlobals().numNoiseTrader, t -> {
            t.wealth = t.getPrng().exponential(100000000).sample();
            t.shortDuration = t.getGlobals().shortSellDuration;
            t.type = Type.Noise;
            System.out.println("Trader type: " + t.type);
          });

      noiseTraderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
      marketGroup.fullyConnected(noiseTraderGroup, Links.TradeLink.class);
    }

    if (getGlobals().numMomentumTrader > 0) {
      Group<MomentumTrader> momentumTraderGroup = generateGroup(MomentumTrader.class,
          getGlobals().numMomentumTrader, t -> {
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

    if (getGlobals().numCoordinatedTrader > 0) {
      Group<CoordinatedTrader> coordinatedTraderGroup = generateGroup(CoordinatedTrader.class,
          getGlobals().numCoordinatedTrader, t -> {
            t.wealth = t.getPrng().exponential(10000000).sample();
            t.shortDuration = t.getGlobals().shortSellDuration;
            t.type = Type.Coordinated;
            t.opinion = getGlobals().ctOpinion;
            System.out.println("Trader type: " + t.type);
          });

      coordinatedTraderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
      marketGroup.fullyConnected(coordinatedTraderGroup, Links.TradeLink.class);

      coordinatedTraderGroup.fullyConnected(socialMediaGroup, Links.SocialNetworkLink.class);
      socialMediaGroup.fullyConnected(coordinatedTraderGroup, Links.SocialNetworkLink.class);
    }

    if (getGlobals().numInfluencer > 0) {
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
            Exchange.sendPriceToTraders,
            Split.create(
                MomentumTrader.updateMomentum,
                Trader.submitLimitOrders
            ),
            Exchange.calcPriceImpact,
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
