package models.trading;

import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;
import simudyne.core.graph.Message.Integer;

/* market only knows the market prices, its demand and supply */
public class Market extends Agent<TradingModel.Globals> {

  @Variable
  public float price;

  private long tick = 0L;

  /* --------- function definitions --------- */
  private static Action<Market> action(SerializableConsumer<Market> consumer) {
    return Action.create(Market.class, consumer);
  }

  public static Action<Market> sendPriceToTraders =
      action(m -> m.getLinks(Links.TradeLink.class).send(Messages.MarketPrice.class, m.price));

  public static Action<Market> calcPriceImpact =
      action(
          m -> {

            // get total amount of buys and sells shares from all traders
            int buys = m.getMessagesOfType(Messages.BuyOrderPlaced.class)
                .stream().mapToInt(Integer::getBody).sum();
            int sells = m.getMessagesOfType(Messages.SellOrderPlaced.class)
                .stream().mapToInt(Integer::getBody).sum();
            int shorts = m.getMessagesOfType(Messages.ShortSellOrderPlaced.class)
                .stream().mapToInt(Integer::getBody).sum();
            int covers = m.getMessagesOfType(Messages.CloseShortPosOrderPlaced.class)
                .stream().mapToInt(Integer::getBody).sum();

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

            m.getDoubleAccumulator("price").add(m.price);
            ++m.tick;
            System.out.println("Time step: " + m.tick + "\n");

            m.getLinks(Links.DataProviderLink.class).send(Messages.NetDemand.class, netDemand);

            /* check if marketShock is triggered */
//            if (m.tick == 50) {
//              m.trueValue = 80; // hard care when testing
//              m.getLinks(Links.TradeLink.class).send(Messages.MarketShock.class, 80);
//            }

          });
}

/* market price will collapse: true value drops

  liquidate: trader holds instead of keep buying more */
