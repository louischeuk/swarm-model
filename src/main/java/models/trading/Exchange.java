package models.trading;

import models.trading.Messages.CloseShortPosOrderPlaced;
import models.trading.Messages.SellOrderPlaced;
import models.trading.Messages.ShortSellOrderPlaced;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;
import simudyne.core.graph.Message.Double;

/* market only knows the market prices, its demand and supply */
public class Exchange extends Agent<Globals> {

  @Variable
  public float price;

  /* --------- function definitions --------- */
  private static Action<Exchange> action(SerializableConsumer<Exchange> consumer) {
    return Action.create(Exchange.class, consumer);
  }

  public static Action<Exchange> sendPriceToTraders =
      action(m -> {
        System.out.println("Tick Count:" + ++ m.getGlobals().tickCount);

        System.out.println("---------------------------------------------------------------------");
        m.getLinks(Links.TradeLink.class).send(Messages.MarketPrice.class, m.price);
        m.getLinks(Links.HedgeFundLink.class).send(Messages.MarketPrice.class, m.price);

      });

  public static Action<Exchange> calcPriceImpact =
      action(
          m -> {

            double buys = m.getMessagesOfType(Messages.BuyOrderPlaced.class)
                .stream().mapToDouble(Double::getBody).sum();
            double sells = m.getMessagesOfType(SellOrderPlaced.class)
                .stream().mapToDouble(Double::getBody).sum();
            double shorts = m.getMessagesOfType(ShortSellOrderPlaced.class)
                .stream().mapToDouble(Double::getBody).sum();
            double covers = m.getMessagesOfType(CloseShortPosOrderPlaced.class)
                .stream().mapToDouble(Double::getBody).sum();

            System.out.println(
                "Total buys shares: " + buys + " (with close short Pos: " + covers + ")");
            System.out.println(
                "Total sell shares: " + sells + " (with short-sell shares: " + shorts + ")");

            double netDemand = buys - sells;
            System.out.println("Net demand: " + netDemand);

            if (netDemand != 0) {
              double lambda = m.getGlobals().lambda;
              float priceChange = (float) (netDemand * lambda);
              System.out.println("Price change: " + priceChange);

              m.price = (m.price + priceChange) < 0 ? 0 : (m.price + priceChange);
            }

            System.out.println("Current market price: " + m.price);
            m.getDoubleAccumulator("price").add(m.price);
            m.getLinks(Links.DataProviderLink.class).send(Messages.NetDemand.class, netDemand);

            /* ---------------- */
            m.getLinks(Links.HedgeFundLink.class).send(Messages.MarketPrice.class, m.price);
            /* ---------------- */

          });
}

/* market price will collapse: true value drops

  liquidate: trader holds instead of keep buying more */
