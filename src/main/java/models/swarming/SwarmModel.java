package models.swarming;

import models.swarming.Trader.Type;
import simudyne.core.abm.AgentBasedModel;
import simudyne.core.abm.Group;
import simudyne.core.abm.Sequence;
import simudyne.core.abm.Split;
import simudyne.core.annotations.Input;
import simudyne.core.annotations.ModelSettings;

@ModelSettings(macroStep = 200)
public class SwarmModel extends AgentBasedModel<Globals> {

  @Input(name = "Market price")
  public float marketPrice = 30.0F;

  @Input(name = "Market equilibrium")
  public double trueValue = 30.0; /* called v_0 in paper */

  /* ------------------- model initialisation -------------------*/
  @Override
  public void init() {
    createDoubleAccumulator("buys", "Number of buy orders");
    createDoubleAccumulator("sells", "Number of sell orders");
    createDoubleAccumulator("shorts", "Number of short sell orders");           /* inclusive */
    createDoubleAccumulator("closeShorts", "Number of short position covered"); /* inclusive */
    createDoubleAccumulator("price", "Market price");
    createDoubleAccumulator("opinions", "Opinions");
    createDoubleAccumulator("equilibrium", "Market equilibrium");
    createDoubleAccumulator("MtDemand", "Momentum getDemand()");
    createDoubleAccumulator("Wealth", "Wealth of traders");
    createDoubleAccumulator("marginAcct", "Margin acct of traders");

    registerAgentTypes(
        Exchange.class, DataProvider.class,
        SocialNetwork.class, Influencer.class,
        FundamentalTrader.class, NoiseTrader.class,
        MomentumTrader.class, CoordinatedTrader.class,
        HedgeFundShort.class
        , HedgeFundLong.class
        , HedgeFundShort2.class
    );

    registerLinkTypes(Links.TradeLink.class,
        Links.SocialNetworkLink.class,
        Links.DataProviderLink.class,
        Links.HedgeFundLink.class);
  }

  @Override
  public void setup() {

    /* ---------------------- Groups creation ---------------------- */
    Group<SocialNetwork> socialMediaGroup = null;
    if (getGlobals().numSocialMedia > 0) {
      socialMediaGroup = generateGroup(SocialNetwork.class, getGlobals().numSocialMedia);
    }

    Group<DataProvider> dataProviderGroup = null;
    if (getGlobals().numDataProvider > 0) {
      dataProviderGroup = generateGroup(DataProvider.class, getGlobals().numDataProvider,
          d -> d.trueValue = trueValue);
      dataProviderGroup.fullyConnected(socialMediaGroup, Links.SocialNetworkLink.class);
    }

    Group<Exchange> exchangeGroup = null;
    if (getGlobals().numExchange > 0) {
      exchangeGroup = generateGroup(Exchange.class, getGlobals().numExchange,
          m -> m.price = marketPrice);
      exchangeGroup.fullyConnected(dataProviderGroup, Links.DataProviderLink.class);
    }

    // expect 1 agent from SocialMedia, DataProvider, Exchange class
    assert exchangeGroup != null;
    assert dataProviderGroup != null;
    assert socialMediaGroup != null;

    /* ---------------------- market participants ---------------------- */

    if (getGlobals().numFundamentalTrader > 0) {
      Group<FundamentalTrader> fundamentalTraderGroup = generateGroup(FundamentalTrader.class,
          getGlobals().numFundamentalTrader, t -> {
            t.type = Type.Fundamental;
            t.wealth = t.getPrng().pareto(getGlobals().wealth, 3).sample();
            t.zScore = t.getPrng().normal(0, 1).sample();
            t.intrinsicValue = trueValue + t.zScore * getGlobals().sigma_u;

            System.out.println("Trader type: " + t.type);
          });

      fundamentalTraderGroup.fullyConnected(exchangeGroup, Links.TradeLink.class);
      exchangeGroup.fullyConnected(fundamentalTraderGroup, Links.TradeLink.class);
      dataProviderGroup.fullyConnected(fundamentalTraderGroup, Links.DataProviderLink.class);
    }

    if (getGlobals().numNoiseTrader > 0) {
      Group<NoiseTrader> noiseTraderGroup = generateGroup(NoiseTrader.class,
          getGlobals().numNoiseTrader, t -> {
            t.wealth = t.getPrng().pareto(getGlobals().wealth, 3).sample();
            t.type = Type.Noise;
            System.out.println("Trader type: " + t.type);
          });

      noiseTraderGroup.fullyConnected(exchangeGroup, Links.TradeLink.class);
      exchangeGroup.fullyConnected(noiseTraderGroup, Links.TradeLink.class);
    }

    if (getGlobals().numMomentumTrader > 0) {
      Group<MomentumTrader> momentumTraderGroup = generateGroup(MomentumTrader.class,
          getGlobals().numMomentumTrader, t -> {
            t.wealth = t.getPrng().pareto(getGlobals().wealth, 3).sample();

            t.mtParams_beta = getGlobals().mtParams_beta;

            t.opinion = t.getPrng().normal(0, 1).sample();
            t.type = Type.Momentum;
            System.out.println("Trader type: " + t.type);
          });

      momentumTraderGroup.fullyConnected(exchangeGroup, Links.TradeLink.class);
      exchangeGroup.fullyConnected(momentumTraderGroup, Links.TradeLink.class);

      momentumTraderGroup.fullyConnected(socialMediaGroup, Links.SocialNetworkLink.class);
      socialMediaGroup.fullyConnected(momentumTraderGroup, Links.SocialNetworkLink.class);
    }

    if (getGlobals().numCoordinatedTrader > 0) {
      Group<CoordinatedTrader> coordinatedTraderGroup = generateGroup(CoordinatedTrader.class,
          getGlobals().numCoordinatedTrader, t -> {
            t.wealth = t.getPrng().pareto(getGlobals().wealth, getGlobals().pareto_params).sample();
            t.sigma_ct = getGlobals().sigma_ct;

            t.type = Type.Coordinated;
            t.opinion = getGlobals().ctOpinion;
            System.out.println("Trader type: " + t.type);
          });

      coordinatedTraderGroup.fullyConnected(exchangeGroup, Links.TradeLink.class);
      exchangeGroup.fullyConnected(coordinatedTraderGroup, Links.TradeLink.class);

      coordinatedTraderGroup.fullyConnected(socialMediaGroup, Links.SocialNetworkLink.class);
      socialMediaGroup.fullyConnected(coordinatedTraderGroup, Links.SocialNetworkLink.class);
    }

//    if (getGlobals().numInfluencer > 0) {
//      Group<Influencer> influencerGroup = generateGroup(Influencer.class,
//          getGlobals().numInfluencer,
//          i -> {
//            i.opinion = getGlobals().influencerOpinion;
//            System.out.println("Influencer created");
//          });
//
//      influencerGroup.fullyConnected(socialMediaGroup, Links.SocialNetworkLink.class);
//      socialMediaGroup.fullyConnected(influencerGroup, Links.SocialNetworkLink.class);
//    }


    /* ------------------------- */
    if (getGlobals().numHedgeFund > 0) {
      Group<HedgeFundShort> hedgeFundShortGroup = generateGroup(HedgeFundShort.class,
          getGlobals().numHedgeFund);
      hedgeFundShortGroup.fullyConnected(exchangeGroup, Links.HedgeFundLink.class);
      exchangeGroup.fullyConnected(hedgeFundShortGroup, Links.HedgeFundLink.class);

      dataProviderGroup.fullyConnected(hedgeFundShortGroup, Links.DataProviderLink.class);
      System.out.println("Hedge fund short");
    }
    /* ------------------------- */

//    Group<HedgeFundLong> hedgeFund2Group = generateGroup(HedgeFundLong.class,1);
//    hedgeFund2Group.fullyConnected(exchangeGroup, Links.HedgeFundLink.class);
//    exchangeGroup.fullyConnected(hedgeFund2Group, Links.HedgeFundLink.class);
//    dataProviderGroup.fullyConnected(hedgeFund2Group, Links.DataProviderLink.class);

    Group<HedgeFundShort2> hedgeFundShort2Group = generateGroup(HedgeFundShort2.class, 1);
    hedgeFundShort2Group.fullyConnected(exchangeGroup, Links.HedgeFundLink.class);
    exchangeGroup.fullyConnected(hedgeFundShort2Group, Links.HedgeFundLink.class);

    dataProviderGroup.fullyConnected(hedgeFundShort2Group, Links.DataProviderLink.class);
    System.out.println("Hedge fund short 2");

    super.setup();
  }

  @Override
  public void step() {
    super.step();

    Sequence subSequencePrice =
        Sequence.create(
            Exchange.sendPriceToTraders,
            Split.create(
                Trader.submitOrders,
                MomentumTrader.updateMomentum
                , HedgeFundShort.submitOrders // ------- Hedge Fund short
                , HedgeFundLong.submitOrders // ------- Hedge Fund long
                , HedgeFundShort2.submitOrders // -------- Hedge Fund short 2
            ),
            Exchange.calcPriceImpact,
            DataProvider.updateTrueValue
        );

    Split subSequenceShareOpinions =
        Split.create(
            Influencer.shareOpinion, // ------- Influencer
            OpDynTrader.shareOpinion // only Momentum and Coordinated
        );

    Split subSequenceUpdateOpinion =
        Split.create(
            OpDynTrader.updateOpinion // Coordinate: (opinion increase with true value rising?
            , Influencer.updateOpinion  // influencer update opinion by true value? (slow ball effort)

        );  // wealth affect opinion?

    /* ------------------------------------------------- */
    run(
        Sequence.create(
            Split.create(
                subSequenceShareOpinions,
                subSequencePrice
            ),
            Split.create(
                FundamentalTrader.updateIntrinsicValue,
                Sequence.create(

                    Split.create(
                        HedgeFundShort.updateStrategy, // ------ Hedge Fund short
                        HedgeFundLong.updateStrategy, // ------- Hedge Fund long
                        HedgeFundShort2.updateStrategy,
                        SocialNetwork.publishOpinionsAndDeltaTrueValue
                    ),
                    subSequenceUpdateOpinion
                )
            )

        )
    );

  }
}
