package models.trading;

import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;
import simudyne.core.graph.Message.Double;

public class Market extends Agent<TradingModel.Globals> {

  @Variable
  public double price;

  int numTraders;

  int timeStep = 0;

  int marketShockStep = 100;

  private static Action<Market> action(SerializableConsumer<Market> consumer) {
    return Action.create(Market.class, consumer);
  }

  public static Action<Market> calcPriceImpact =
      Action.create(Market.class,
          m -> {
            // get total amount of buys and sells shares for all agents
            double buys = m.getMessagesOfType(Messages.BuyOrderPlaced.class).stream()
                .mapToDouble(Double::getBody).sum();
            double sells = m.getMessagesOfType(Messages.SellOrderPlaced.class).stream()
                .mapToDouble(Double::getBody).sum();
            double shorts = m.getMessagesOfType(Messages.ShortSellOrderPlaced.class).stream()
                .mapToDouble(Double::getBody).sum();
            double covers = m.getMessagesOfType(Messages.CoverShortPosOrderPlaced.class).stream()
                .mapToDouble(Double::getBody).sum();

            System.out.println(
                "Total buys shares: " + buys + " (with cover short Pos: " + covers + ")");
            System.out.println(
                "Total sell shares: " + sells + " (with short-sell shares: " + shorts + ")");
            System.out.println("Total volume: " + (buys + sells));

            double netDemand = buys - sells;
            System.out.println("Net demand: " + netDemand);

            if (netDemand == 0) {
              m.getLinks(Links.TradeLink.class)
                  .send(Messages.MarketPrice.class, m.getGlobals().marketPrice);
              m.getDoubleAccumulator("price").add(m.getGlobals().marketPrice);

            } else {
              double lambda = m.getGlobals().lambda;
              double priceChange = netDemand * lambda;

              System.out.println("Price change: " + priceChange + "\n");

              m.getGlobals().marketPrice += priceChange;
              m.price = m.getGlobals().marketPrice; // to see in console

              m.getDoubleAccumulator("price").add(m.getGlobals().marketPrice);
              m.getLinks(Links.TradeLink.class)
                  .send(Messages.MarketPrice.class, m.getGlobals().marketPrice);
            }

            // if marketShock is triggered
//            if (++m.timeStep == m.marketShockStep) {
//              m.getGlobals().isMarketShockTriggered = true;
//              m.getLinks(Links.TradeLink.class)
//                  .send(Messages.MarketShock.class, m.marketShockStep);
//            }
//            System.out.println("Time step: " + m.timeStep);

          });

}
