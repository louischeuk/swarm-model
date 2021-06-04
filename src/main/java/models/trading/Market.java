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

  int marketShockStep = 50;

  private static Action<Market> action(SerializableConsumer<Market> consumer) {
    return Action.create(Market.class, consumer);
  }

  public static Action<Market> calcPriceImpact =
      action(
          m -> {

            m.getGlobals().priceHistory.add(m.price);

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

              System.out.println("Price change: " + priceChange);

              m.getGlobals().marketPrice += priceChange;

              if (m.getGlobals().marketPrice < 0) {
                m.getGlobals().marketPrice = 0;
              }

              m.price = m.getGlobals().marketPrice; // to see in console

              m.getDoubleAccumulator("price").add(m.getGlobals().marketPrice);
              m.getLinks(Links.TradeLink.class)
                  .send(Messages.MarketPrice.class, m.getGlobals().marketPrice);
            }

            // check if marketShock is triggered
            if (++m.timeStep == m.marketShockStep) {
//              m.triggerMarketShock();
            }
            System.out.println("Time step: " + m.timeStep + "\n");
          });

  private void triggerMarketShock() {
    getGlobals().trueValue = 150; // hard care when testing
    getLinks(Links.TradeLink.class).send(Messages.MarketShock.class, marketShockStep);

  }

  /* update true value V(t) - random walk: */
  public static Action<Market> updateTrueValue =
      action(m -> {

        /*
           sum of jump size = Y_i * N_t = N(0,s_j) * P(lambda)   Note. s_j may be == s_v
           sd_j = 1, lambda = 2
        */
        double jumpDiffusionProcess =
            m.getPrng().normal(0, 3).sample()
                * m.getPrng().poisson(2).sample();

        System.out.println("jumpDiffusionProcess: " + jumpDiffusionProcess);

        System.out.println("prev True value: " + m.getGlobals().trueValue);

        /*
           V(t) = V(t – 1) + N(0,sd_v) + jump diffusion process
                = V(t – 1) + N(0,sd_v) + summation[i, N_t](Y_i)
        */
        m.getGlobals().trueValue =
            m.getGlobals().trueValue
            + m.getPrng().normal(0, m.getGlobals().stdDev).sample()
            + jumpDiffusionProcess;

        if (m.getGlobals().trueValue < 0) {
          m.getGlobals().trueValue = 0;
        }

        System.out.println("New True value: " + m.getGlobals().trueValue);
      });


}
