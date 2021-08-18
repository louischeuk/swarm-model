package models.swarming;

import models.swarming.Messages.CloseShortPosOrderPlaced;
import models.swarming.Messages.SellOrderPlaced;
import models.swarming.Messages.ShortSellOrderPlaced;
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
      action(e -> {
        e.getGlobals().tickCount++;
        System.out.println("Tick Count:" + e.getGlobals().tickCount);
        System.out.println("---------------------------------------------------------------------");
        e.getLinks(Links.TradeLink.class).send(Messages.MarketPrice.class, e.price);
        e.getLinks(Links.HedgeFundLink.class).send(Messages.MarketPrice.class, e.price);

      });

  public static Action<Exchange> calcPriceImpact =
      action(
          e -> {

            ///////////// testing ////////////
            if (e.getGlobals().tickCount == 50) {
              e.getGlobals().pMomentumTrade = 0.1;
              e.getGlobals().pCoordinatedTrade = 0.1;
            }
            ////////////////

            double buys = e.getMessagesOfType(Messages.BuyOrderPlaced.class)
                .stream().mapToDouble(Double::getBody).sum();
            double sells = e.getMessagesOfType(SellOrderPlaced.class)
                .stream().mapToDouble(Double::getBody).sum();
            double shorts = e.getMessagesOfType(ShortSellOrderPlaced.class)
                .stream().mapToDouble(Double::getBody).sum();
            double covers = e.getMessagesOfType(CloseShortPosOrderPlaced.class)
                .stream().mapToDouble(Double::getBody).sum();

            System.out.println(
                "Total buys shares: " + buys + " (with close short Pos: " + covers + ")");
            System.out.println(
                "Total sell shares: " + sells + " (with short-sell shares: " + shorts + ")");

            double netDemand = buys - sells;
            System.out.println("Net demand: " + netDemand);

            if (netDemand != 0) {
              double lambda = e.getGlobals().lambda;
              float priceChange = (float) (netDemand * lambda);
              System.out.println("Price change: " + priceChange);

              e.price = (e.price + priceChange) < 0 ? 0 : (e.price + priceChange);
            }

            System.out.println("Current market price: " + e.price);
            e.getDoubleAccumulator("price").add(e.price);
            e.getLinks(Links.DataProviderLink.class).send(Messages.NetDemand.class, netDemand);

            /* ---------------- */
            e.getLinks(Links.HedgeFundLink.class).send(Messages.MarketPrice.class, e.price);
            /* ---------------- */

          });
}

/* market price will collapse: true value drops

  liquidate: trader holds instead of keep buying more */
