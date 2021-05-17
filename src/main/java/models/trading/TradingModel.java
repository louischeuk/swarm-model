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

    @Input(name = "Update Frequency")
    public double updateFrequency = 0.01;

    @Input(name = "Lambda")
    public double lambda = 10;

    @Input(name = "Volatility of Information Signal")
    public double volatilityInfo = 0.001;

    public double informationSignal;
  }

  @Override
  public void init() {
    createLongAccumulator("buys", "Number of buy orders");
    createLongAccumulator("sells", "Number of sell orders");
    createDoubleAccumulator("price", "Market Price");

    registerAgentTypes(Market.class, Trader.class, UtilityTradersGroup.class);
    registerLinkTypes(Links.TradeLink.class, Links.UtilityTraders.class);
  }

  /**
   * Gaussian random walk the information signal, with variance of the volatility input.
   */
  private void updateSignal() {
    getGlobals().informationSignal =
        getContext().getPrng().gaussian(0, getGlobals().volatilityInfo).sample();
  }

  @Override
  public void setup() {
    updateSignal();

    Group<Trader> traderGroup = generateGroup(Trader.class, numTrader);
    Group<Market> marketGroup = generateGroup(Market.class, 1,
        market -> market.numTraders = numTrader);

    Group<UtilityTradersGroup> utilityTradersGroup = generateGroup(UtilityTradersGroup.class, 1);
    traderGroup.fullyConnected(utilityTradersGroup, Links.UtilityTraders.class);

    traderGroup.fullyConnected(marketGroup, Links.TradeLink.class);
    marketGroup.fullyConnected(traderGroup, Links.TradeLink.class);

    super.setup();
  }

  @Override
  public void step() {
    super.step();

    updateSignal();

    run(Trader.processInformation(), Market.calcPriceImpact(), Trader.updateThreshold());
  }
}
