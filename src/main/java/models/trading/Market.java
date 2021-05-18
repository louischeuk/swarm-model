package models.trading;

import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public class Market extends Agent<TradingModel.Globals> {

  @Variable
  public double price;
  int numTraders;

  @Override
  public void init() {
    price = getGlobals().marketPrice;
  }

  private static Action<Market> action(SerializableConsumer<Market> consumer) {
    return Action.create(Market.class, consumer);
  }

  public static Action<Market> calcPriceImpact() {
    return action(
        market -> {
          int buys = market.getMessagesOfType(Messages.BuyOrderPlaced.class).size();
          int sells = market.getMessagesOfType(Messages.SellOrderPlaced.class).size();

          int netDemand = buys - sells;

          if (netDemand == 0) {
            market.getLinks(Links.TradeLink.class).send(Messages.MarketPriceChange.class, 0);
          } else {
            double lambda = market.getGlobals().lambda;
            double priceChange = (netDemand / (double) market.numTraders) / lambda;
            market.getGlobals().marketPrice += priceChange;
            market.price = market.getGlobals().marketPrice; // for console

            market.getDoubleAccumulator("price").add(market.getGlobals().marketPrice);
            market
                .getLinks(Links.TradeLink.class)
                .send(Messages.MarketPriceChange.class, priceChange);
          }
        });
  }
}
