package models.trading;

import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;
import simudyne.core.graph.Message.Integer;

public class Market extends Agent<TradingModel.Globals> {

  @Variable
  public float price;

  @Variable
  public double trueValue;

  private double accumulatedNetDemand;

  private long tick = 0L;

  /* --------- function definitions --------- */

  private static Action<Market> action(SerializableConsumer<Market> consumer) {
    return Action.create(Market.class, consumer);
  }

  public static Action<Market> sendPriceToTraders =
      action(m -> {
        m.getLinks(Links.TradeLink.class).send(Messages.MarketPrice.class, m.price);
        m.getLinks(Links.TradeLink.class).send(Messages.Tick.class, ++m.tick);
      });


  public static Action<Market> calcPriceImpact =
      action(
          m -> {
            System.out.println("hello");

            // get total amount of buys and sells shares for all agents
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
            m.accumulatedNetDemand += netDemand;
            System.out.println("Accumulated net demand: " + m.accumulatedNetDemand);

            if (netDemand != 0) {
              double lambda = m.getGlobals().lambda;
              float priceChange = (float) (netDemand * lambda);
              System.out.println("Price change: " + priceChange);

              m.price = (m.price + priceChange) < 0 ? 0 : (m.price + priceChange);
            }

            m.getDoubleAccumulator("price").add(m.price);
            System.out.println("Time step: " + m.tick + "\n");

            /* check if marketShock is triggered */
//            if (m.tick == 50) {
//              m.trueValue = 80; // hard care when testing
//              m.getLinks(Links.TradeLink.class).send(Messages.MarketShock.class, 80);
//            }

          });

  /* update true value V(t) - random walk: */
  public static Action<Market> updateTrueValue =
      action(
          m -> {
            /*
               sum of jump size = Y_i * N_t = N(0,s_j) * P(lambda)   Note. s_j may be == s_v
               sd_j = 1, lambda = 2
            */
            double jumpDiffusionProcess =
                m.getPrng().normal(0, MarketParams.jumpDiffusionStdDev).sample()
                    * m.getPrng().poisson(MarketParams.jumpDiffusionPoissonLambda).sample();
            System.out.println("jumpDiffusionProcess: " + jumpDiffusionProcess);

            /* Random walk
               V(t) = V(t – 1) + N(0,sd_v) + jump diffusion process
                    = V(t – 1) + N(0,sd_v) + summation[i, N_t](Y_i)
            */
            System.out.println("prev True value: " + m.trueValue);

            double randomWalk =
                m.getPrng().normal(0, m.getGlobals().stdDev).sample() + jumpDiffusionProcess;

            double marketSignal = m.getGlobals().lambda * 2 / 3 * m.accumulatedNetDemand;
            System.out.println("Market signal: " + marketSignal);

//            m.trueValue = m.trueValue + randomWalk + marketSignal;
//            m.trueValue = m.trueValue < 0 ? 0 : m.trueValue;

//            m.trueValue = m.trueValue + marketSignal;
            System.out.println("New True value: " + m.trueValue);
            m.getLinks(Links.TradeLink.class).send(Messages.TrueValue.class, m.trueValue);

//
//            double trueValue = m.trueValue + marketSignal;
//            System.out.println("New True value: " + trueValue);
//            m.getLinks(Links.TradeLink.class).send(Messages.TrueValue.class, trueValue);

          });


}
