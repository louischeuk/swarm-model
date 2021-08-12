package models.trading;

import models.trading.Trader.Type;
import simudyne.core.abm.*;
import simudyne.core.annotations.Input;
import simudyne.core.annotations.ModelSettings;

@ModelSettings(macroStep = 200)
public class SwarmModel extends AgentBasedModel<Globals> {

    @Input(name = "Market price")
    public float marketPrice = 10.0F;

    @Input(name = "Market equilibrium")
    public double trueValue = 10.0; /* called v_0 in paper */

    //TODO: Globals class is quite large, I would pull this out as it's own class


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

        registerAgentTypes(
                Exchange.class, DataProvider.class,
                SocialNetwork.class, Influencer.class,
                FundamentalTrader.class, NoiseTrader.class, MomentumTrader.class, CoordinatedTrader.class,
                HedgeFund.class
        );

        registerLinkTypes(Links.TradeLink.class,
                Links.SocialNetworkLink.class,
                Links.DataProviderLink.class,
                Links.HedgeFundLink.class);
    }

    @Override
    public void setup() {

        //TODO: lots of hardcoded agent numbers, expose these in the console or to a config file
        /* ---------------------- Groups creation ---------------------- */
        Group<SocialNetwork> socialMediaGroup = generateGroup(SocialNetwork.class, 1);

        Group<DataProvider> dataProviderGroup = generateGroup(DataProvider.class, 1,
                d -> d.trueValue = trueValue);

        Group<Exchange> exchangeGroup = generateGroup(Exchange.class, 1, m -> m.price = marketPrice);
        exchangeGroup.fullyConnected(dataProviderGroup, Links.DataProviderLink.class);

        /* ------------------------- */
        if (getGlobals().numHedgeFund > 0) {
            Group<HedgeFund> hedgeFundGroup = generateGroup(HedgeFund.class, 1);
            hedgeFundGroup.fullyConnected(exchangeGroup, Links.HedgeFundLink.class);
            exchangeGroup.fullyConnected(hedgeFundGroup, Links.HedgeFundLink.class);
            System.out.println("Hedge fund");
        }
        /* ------------------------- */

        if (getGlobals().numFundamentalTrader > 0) {
            Group<FundamentalTrader> fundamentalTraderGroup = generateGroup(FundamentalTrader.class,
                    getGlobals().numFundamentalTrader, t -> {
                        t.type = Type.Fundamental;
                        //TODO: Turn shape value into an input parameter
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
                        //TODO: Turn shape value into an input parameter

                        t.wealth = t.getPrng().pareto(getGlobals().wealth, 3).sample();

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
                        //TODO: Turn shape value into an input parameter
                        t.wealth = t.getPrng().pareto(getGlobals().wealth, 3).sample();
                        t.type = Type.Coordinated;
                        t.opinion = getGlobals().ctOpinion;
                        System.out.println("Trader type: " + t.type);
                    });

            coordinatedTraderGroup.fullyConnected(exchangeGroup, Links.TradeLink.class);
            exchangeGroup.fullyConnected(coordinatedTraderGroup, Links.TradeLink.class);

            coordinatedTraderGroup.fullyConnected(socialMediaGroup, Links.SocialNetworkLink.class);
            socialMediaGroup.fullyConnected(coordinatedTraderGroup, Links.SocialNetworkLink.class);
        }

        if (getGlobals().numInfluencer > 0) {
            Group<Influencer> influencerGroup = generateGroup(Influencer.class, 1,
                    i -> {
                        i.opinion = getGlobals().influencerOpinion; /* try 100 to have a nice uptrend of market price */
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
                                Trader.submitOrders
                                , HedgeFund.submitOrders // ------- Hedge Fund
                        ),
                        Exchange.calcPriceImpact,
                        Split.create(
                                DataProvider.updateTrueValue
                        )
                );

        Sequence subSequenceOpinion =
                Sequence.create(
                        Split.create(
                                //TODO: all the participants should share their opinion, this can then be called from the base class
//                Influencer.shareOpinion, // ------- Influencer
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
                        FundamentalTrader.adjustIntrinsicValue
                )
        );

    }
}
