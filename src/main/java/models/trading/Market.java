package models.trading;

import java.util.List;
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

  int marketShockStep = 10;

  @Override
  public void init() {
    price = getGlobals().marketPrice;
  }

  private static Action<Market> action(SerializableConsumer<Market> consumer) {
    return Action.create(Market.class, consumer);
  }

  public static Action<Market> calcPriceImpact() {
    return action(
        m -> {
          List<Messages.BuyOrderPlaced> buyMessages =
              m.getMessagesOfType(Messages.BuyOrderPlaced.class);
          List<Messages.SellOrderPlaced> sellMessages =
              m.getMessagesOfType(Messages.SellOrderPlaced.class);
          List<Messages.ShortSellOrderPlaced> shortSellMessages =
              m.getMessagesOfType(Messages.ShortSellOrderPlaced.class);
          List<Messages.CoverShortPosOrderPlaced> coverShortPosMessages =
              m.getMessagesOfType(Messages.CoverShortPosOrderPlaced.class);

          // get total amount of buys and sells shares for all agents
          double buys = buyMessages.stream().mapToDouble(Double::getBody).sum();
          double sells = sellMessages.stream().mapToDouble(Double::getBody).sum();
          double shorts = shortSellMessages.stream().mapToDouble(Double::getBody).sum();
          double covers = coverShortPosMessages.stream().mapToDouble(Double::getBody).sum();

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
            double priceChange = netDemand * lambda; // what should lambda be?

            System.out.println("Price change: " + priceChange);
            System.out.println();

            m.getGlobals().marketPrice += priceChange;
            m.price = m.getGlobals().marketPrice; // to see in console

            m.getDoubleAccumulator("price").add(m.getGlobals().marketPrice);
            m.getLinks(Links.TradeLink.class)
                .send(Messages.MarketPrice.class, m.getGlobals().marketPrice);
          }

          // if marketShock is triggered
          if (++m.timeStep == m.marketShockStep) {
            m.getGlobals().isMarketShockTriggered = true;
            m.getLinks(Links.TradeLink.class)
                .send(Messages.MarketShock.class, m.timeStep);
          }

          System.out.println("Time step: " + m.timeStep);

        });
  }
}
