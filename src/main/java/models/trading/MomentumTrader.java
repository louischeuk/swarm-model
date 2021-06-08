package models.trading;

import java.util.HashMap;
import models.trading.Messages.HistoricalPrices;
import simudyne.core.abm.Action;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public class MomentumTrader extends Trader {

  long shortTermMALookBackPeriod = 7;
  long longTermMALookBackPeriod = 21;
  int crossover = 0; /* 1: upward, 0: no change, -1: downward */

  @Variable
  public double shortTermSMA;

  @Variable
  public double longTermSMA;

  @Variable
  public double shortTermEMA;

  @Variable
  public double longTermEMA;

  private static Action<MomentumTrader> action(SerializableConsumer<MomentumTrader> consumer) {
    return Action.create(MomentumTrader.class, consumer);
  }

  @Override
  protected void tradeStrategy() {

    System.out.println("********* momentum trader strategy *********");

    long tick = getMessageOfType(Messages.Tick.class).getBody(); // note. 1 tick behind here
    System.out.println("tick: " + tick);

    if (tick > longTermMALookBackPeriod) {
      if (getPrng().uniform(0, 1).sample() < getGlobals().momentumTraderActivity) {

        int volume = (int) getPrng().normal(0, getGlobals().stdDev).sample();
        System.out.println("Volume: " + volume);

        switch (crossover) {
          case 1:
            System.out.println("Time to buy");
            handleWhenBuyShares(volume);
            break;
          case -1:
            System.out.println("Time to sell");
            handleWhenSellShares(Math.abs(volume));
            break;
          case 0:
            System.out.println("momentum trader holds");
            hold();
            break;
        }

      }
    }
  }

  /* ----------- SMA --------------- */
  private double getShortTermSMA(long tick) {
    return getSMA(shortTermMALookBackPeriod, tick);
  }

  private double getLongTermSMA(long tick) {
    return getSMA(longTermMALookBackPeriod, tick);
  }

  private double getSMA(long lookBackPeriod, long tick) {

    HashMap<Long, Double> historicalPrices = getMessageOfType(
        HistoricalPrices.class).historicalPrices;

    double SMA = 0;
    int count = 0;
    for (long i = tick; i > tick - lookBackPeriod; i--) {
      SMA += historicalPrices.get(i);
      count++;
    }

    System.out.println("Days counted: " + count);
    return SMA / lookBackPeriod;
  }

  /* ----------- EMA --------------- */
  private double getShortTermEMA(long tick) {
    return getEMA(shortTermMALookBackPeriod, shortTermEMA, tick);
  }

  private double getLongTermEMA(long tick) {
    return getEMA(longTermMALookBackPeriod, longTermEMA, tick);
  }

  private double getEMA(long lookBackPeriod, double yesterdayEMA, long tick) {

    double closingPrice = getMessageOfType(Messages.MarketPrice.class).getBody();
    double multiplier = getGlobals().smoothing / (1 + lookBackPeriod);

    /* EMA = Closing price x multiplier + EMA (previous day) x (1-multiplier) */
    return closingPrice * multiplier + yesterdayEMA * (1 - multiplier);
  }

  public static Action<MomentumTrader> calcMA =
      action(t -> {
        long tick = t.getMessageOfType(Messages.Tick.class).getBody();
        System.out.println("tick at momentum trader calMA action: " + tick);

        if (tick == t.longTermMALookBackPeriod) { // first SMA / EMA
          System.out.println("get first SMA and LMA at tick " + tick);
          t.shortTermSMA = t.getShortTermSMA(tick);
          t.longTermSMA = t.getLongTermSMA(tick);

          t.shortTermEMA = t.shortTermSMA;
          t.longTermEMA = t.shortTermEMA;
        }

        if (tick > t.longTermMALookBackPeriod) {
          double curShortTermSMA = t.getShortTermSMA(tick);
          double curTermTermSMA = t.getLongTermSMA(tick);
//
////          System.out.println("prev short-term SMA: " + t.shortTermSMA);
////          System.out.println("prev long-term SMA: " + t.longTermSMA);
////          System.out.println("cur short-term SMA: " + curShortTermSMA);
////          System.out.println("cur long-term SMA: " + curTermTermSMA);
//
//          if (t.shortTermSMA < t.longTermSMA && curShortTermSMA >= curTermTermSMA) {
//            t.crossover = 1;
//          } else if (t.shortTermSMA > t.longTermSMA && curShortTermSMA <= curTermTermSMA) {
//            t.crossover = -1;
//          } else {
//            t.crossover = 0;
//          }
//
          // cur moving averages becomes yesterday moving averages
          t.shortTermSMA = curShortTermSMA;
          t.longTermSMA = curTermTermSMA;



          /* EMA version */
          double curShortTermEMA = t.getShortTermEMA(tick);
          double curTermTermEMA = t.getLongTermEMA(tick);

          if (t.shortTermEMA < t.longTermEMA && curShortTermEMA >= curTermTermEMA) {
            t.crossover = 1;
          } else if (t.shortTermEMA > t.longTermEMA && curShortTermEMA <= curTermTermEMA) {
            t.crossover = -1;
          } else {
            t.crossover = 0;
          }

          // cur moving averages becomes yesterday moving averages
          t.shortTermEMA = curShortTermEMA;
          t.longTermEMA = curTermTermEMA;

        }
      });
}
