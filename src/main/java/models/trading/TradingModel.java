package models.trading;

import models.trading.Trader.Type;
import simudyne.core.abm.AgentBasedModel;
import simudyne.core.abm.GlobalState;
import simudyne.core.abm.Group;
import simudyne.core.abm.Split;
import simudyne.core.annotations.Constant;
import simudyne.core.annotations.Input;
import simudyne.core.annotations.ModelSettings;

@ModelSettings(macroStep = 100)
public class TradingModel extends AgentBasedModel<TradingModel.Globals> {

  @Constant(name = "Number of Traders")
  public int numTrader = 20;

  @Constant(name = "Real value of market price")
  public double realValue = 50;

  public static final class Globals extends GlobalState {

    @Input(name = "Market Price")
    public double marketPrice = 50.0;

    // speed at which the market price converges market equilibrium
    @Input(name = "Exchange's Lambda / Price Elasticity")
    public double lambda = 0.15;

    @Input(name = "Standard deviation") // for normal distribution
    public double stdDev = 10;

    @Input(name = "Short Selling duration")
    public int shortSellDuration = 200;

    @Input(name = "max times of short sell in process")
    public int maxShortingInProcess = 200;

    @Input(name = "Sensitivity to market")
    public double sensitivity = 0.015;     /*
                                              tune this w.r.t. total amount of traders
                                              20  traders - 0.015
                                              100 traders - 0.015 to 0.07
                                            */

    @Input(name = "Initial Margin Requirement")
    public double initialMarginRequirement = 0.5;

    @Input(name = "Maintenance Margin")
    public double maintenanceMargin = 0.3;

    public boolean isMarketShockTriggered = false;

    @Input(name = "Confidence factor")
    public double confidenceFactor = 0.001;

    @Input(name = "Opinion multiple Factor")
    public double opinionFactor = 100;      /*
                                              tune it w.r.t to number of traders
                                              20  traders - 100
                                              100 traders - 500 to 1000
                                            */
  }

  @Override
  public void init() {
    createLongAccumulator("buys", "Number of buy orders");
    createLongAccumulator("sells", "Number of sell orders");
    createLongAccumulator("shorts", "Number of short sell orders"); // inclusive
    createLongAccumulator("coverShorts", "Number of short position covered"); // inclusive
    createDoubleAccumulator("price", "Market price");

    registerAgentTypes(Market.class, Trader.class);
    registerLinkTypes(Links.TradeLink.class, Links.SocialMediaLink.class);

  }

  @Override
  public void setup() {

    Group<Trader> traderGroup = generateGroup(Trader.class, numTrader, t -> {
      t.intrinsicValue = t.getPrng().normal(realValue, getGlobals().stdDev).sample();
      t.wealth = t.getPrng().exponential(100000000).sample();
//      t.shortDuration = t.getPrng().generator.nextInt(getGlobals().shortSellDuration) + 1;
      t.shortDuration = t.getGlobals().shortSellDuration;
      t.opinion = t.getPrng().uniform(-1, 1).sample();
      t.opinionThresh = t.getPrng().uniform(0, 1).sample();

      // 80%: fundamental-trader | 20%: Noise-trader (haven't implemented anything yet)
      if (t.getID() < numTrader * 0.8) {
        t.type = Type.Fundamental;
      } else {
        t.type = Type.Noise;
      }
      System.out.println("Trader type: " + t.type);

//        if (t.getID() < numTrader * 0.2) {
//          t.type = Type.Coordinated;
//          for (int i = 0; i < numTrader * 0.2; i++) { // group the WSB traders
//            t.addLink(i, Links.CoordinatedLink.class);
//          }

    });

    Group<Market> marketGroup = generateGroup(Market.class, 1,
        market -> {
          market.numTraders = numTrader;
          market.price = getGlobals().marketPrice;
        });

    traderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
    marketGroup.fullyConnected(traderGroup, Links.TradeLink.class);

    traderGroup.fullyConnected(traderGroup, Links.SocialMediaLink.class);

    super.setup();
  }

  @Override
  public void step() {
    super.step();

    run(
        Split.create(
            Trader.processMarketPrice,
            Trader.sendOpinion
        ),
        Split.create(
            Market.calcPriceImpact,
            Trader.updateOpinion
        ),
        Split.create(
            Trader.adjustIntrinsicValue,
            Trader.updateOpinionThreshold
        )
    );

  }
}
