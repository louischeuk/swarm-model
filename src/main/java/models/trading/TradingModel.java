package models.trading;

import models.trading.Links.ToEveryTraderLink;
import models.trading.Trader.Type;
import simudyne.core.abm.AgentBasedModel;
import simudyne.core.abm.GlobalState;
import simudyne.core.abm.Group;
import simudyne.core.annotations.Constant;
import simudyne.core.annotations.Input;
import simudyne.core.annotations.ModelSettings;

@ModelSettings(macroStep = 100)
public class TradingModel extends AgentBasedModel<TradingModel.Globals> {

  @Constant(name = "Number of Traders")
  public int numTrader = 20;

  public static final class Globals extends GlobalState {

    // for the net demand of traded stock
    @Input(name = "Exchange's Lambda / Price Elasticity")
    public double lambda = 0.15;

//    // volatility of market price - use in normal distribution
//    @Input(name = "Sigma, market price volatility")
//    public double sigma = 0.35;

    @Input(name = "Market Price")
    public double marketPrice = 50.0;

    @Input(name = "standard deviation") // for normal distribution
    public double stdDev = 10;

    // weight before buy or sell
    @Input(name = "Weighting")
    public double weighting = 0.5;

    @Input(name = "Short Selling duration")
    public int shortSellDuration = 5;

    @Input(name = "sensitivity")
    public double sensitivity = 0.15;

    @Input(name = "Initial Margin Requirement")
    public double initialMarginRequirement = 0.5;

    @Input(name = "Maintenance Margin")
    public double maintenanceMargin = 0.3;

    public boolean isMarketShockTriggered = false;

  }

  @Override
  public void init() {

    createLongAccumulator("buys", "Number of buy orders");
    createLongAccumulator("sells", "Number of sell orders");
    createLongAccumulator("shorts", "Number of short sell orders"); // inclusive
    createLongAccumulator("coverShorts", "Number of short position covered"); // inclusive
    createDoubleAccumulator("price", "Market price");

    registerAgentTypes(Market.class, Trader.class);
    registerLinkTypes(Links.TradeLink.class,
        Links.CoordinatedLink.class,
        Links.ToEveryTraderLink.class);
  }

  @Override
  public void setup() {
    Group<Trader> traderGroup = generateGroup(Trader.class, numTrader, t -> {
      // 20%: coordinated - 50%: opinionated - 20%: momentum - 10%: ZI

      if (t.getID() < numTrader * 0.2) {
        t.type = Type.Coordinated;
        for (int i = 0; i < numTrader * 0.2; i++) { // group the WSB traders
          t.addLink(i, Links.CoordinatedLink.class);
        }

      } else if (t.getID() >= numTrader * 0.2 && t.getID() < numTrader * 0.7) {
        t.type = Type.Opinionated;

      } else if (t.getID() >= numTrader * 0.7 && t.getID() < numTrader * 0.9) {
        t.type = Type.Momentum;

      } else {
        t.type = Type.ZI;
      }

      System.out.println("Trader type: " + t.type);
    });

    Group<Market> marketGroup = generateGroup(Market.class, 1,
        market -> market.numTraders = numTrader);

    traderGroup.fullyConnected(traderGroup, ToEveryTraderLink.class);

    traderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
    marketGroup.fullyConnected(traderGroup, Links.TradeLink.class);

    super.setup();
  }

  @Override
  public void step() {
    super.step();

    run(Trader.processMarketPrice(),
        Market.calcPriceImpact(),
        Trader.adjustIntrinsicValue());
  }
}
