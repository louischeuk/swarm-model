package models.trading;

import simudyne.core.abm.AgentBasedModel;
import simudyne.core.abm.GlobalState;
import simudyne.core.abm.Group;
import simudyne.core.annotations.Constant;
import simudyne.core.annotations.Input;
import simudyne.core.annotations.ModelSettings;

@ModelSettings(macroStep = 100)
public class TradingModel extends AgentBasedModel<TradingModel.Globals> {

  @Constant(name = "Number of Traders")
  public int numTrader = 100;

  public static final class Globals extends GlobalState {

    @Input(name = "Lambda")
    public double lambda = 0.1;

    @Input // volatility of market price - use in normal distribution
    public double sigma = 0.25;

    @Input(name = "Market Price")
    public double marketPrice = 10.0;

    @Input // weight before buy or sell
    public double weighting = 0.5;

    @Input(name = "Short Selling duration")
    public int shortSellDuration = 5;

    @Input(name = "sensitivity")
    public double sensitivity = 0.15;

    @Input(name = "Initial Margin Requirement")
    public double initialMarginRequirement = 0.5;

    @Input(name = "Maintenance Margin")
    public double maintenanceMargin = 0.3;

  }

  @Override
  public void init() {

    createLongAccumulator("buys", "Number of buy orders");
    createLongAccumulator("sells", "Number of sell orders"); // exclusive
    createLongAccumulator("shorts", "Number of short sell orders"); // exclusive
    createDoubleAccumulator("price", "Market price");

    registerAgentTypes(Market.class, Trader.class);
    registerLinkTypes(Links.TradeLink.class);
  }

  /**
   * Gaussian random walk the information signal, with variance of the volatility input.
   */
//  private void updateSignal() {
//    getGlobals().informationSignal =
//        getContext().getPrng().gaussian(0, getGlobals().volatilityInfo).sample();
//  }

  @Override
  public void setup() {
    Group<Trader> traderGroup = generateGroup(Trader.class, numTrader);
    Group<Market> marketGroup = generateGroup(Market.class, 1,
        market -> market.numTraders = numTrader);

//    traderGroup.fullyConnected(traderGroup, Links.TraderToTraderLink.class);

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
