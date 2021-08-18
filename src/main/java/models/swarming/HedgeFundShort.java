package models.swarming;

import models.swarming.Links.HedgeFundLink;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;


/* Hedge fund: simplified strategy in this model. Assume HF only trades 3 times.
   1. at specified time step: short sell x amount of shares

   2. if the current market price is raised to double of the price at which HF short sold,
   short sell 2x amount of shares to suppress the market price

   3. if the current market price is raised to triple of the price at which HF short sold,
   close all the short sell positions to prevent from suffer even bigger lost

   4. HF is out of market
 */
public class HedgeFundShort extends Agent<Globals> {


  public enum Side {BUY, SELL}

  Side side = Side.SELL;

  @Variable
  public double shares = 0;

  public float curPrice; // record the current price

  @Variable
  public boolean isLeftMarket = false;

  /* -------------------- new --------------------------- */

  // short sell for period 1
  public double volume = 5.0; // change dynamically

  public double tickStartSS = 1; // Short sell at beginning

  public boolean isSSing = true;

  public double timeToStopSS = 5;

  // expected time to cover pos
  public double priceToClosePos = 4; // expect to never happen

  // short sell for period 2 (trigger: when a price is reached)
  public double priceToSecondSS = 60;

  // cover all the pos (trigger: when a price is reached) -> short squeeze
  public double priceToStopLoss = 90; // due to short squeeze

/*
  then at peak high, halt trade
  -> probability to trade change to 0.5 /
  -> only allow sell not buy
  -> limit amount of buy
 */


  public double getAlpha() {

    if (isSSing && getGlobals().tickCount - tickStartSS < timeToStopSS) {
      return 1.0d;
    }

    if (!isSSing && curPrice >= priceToSecondSS) {
      isSSing = true;
      tickStartSS = getGlobals().tickCount;
      return 1.0d; // expect to further sell
    }

    if (curPrice <= priceToClosePos || curPrice >= priceToStopLoss) {
      side = Side.BUY;
      return 1.0d; // buy
    }

    isSSing = false; // re-set
    return 0.0d;
  }

  public Side getSide() { return side; }

  protected double getVolume() { return volume; }

  /* ------------------- functions ------------------- */

  public static Action<HedgeFundShort> action(SerializableConsumer<HedgeFundShort> consumer) {
    return Action.create(HedgeFundShort.class, consumer);
  }

  public static Action<HedgeFundShort> submitOrders =
      action(
          h -> {
            if (h.isLeftMarket) {
              return;
            }

            System.out.println("Hedge fund submits order");
            h.curPrice = h.getMarketPrice();
            double alpha = h.getAlpha();
            if (alpha == 1) {
              Side side = h.getSide();
              switch (side) {
                case BUY:
                  System.out.println("Hedge fund BUY !!!!!!!!");
                  h.buy(Math.abs(h.shares)); // buy back all
                  h.isLeftMarket = true;
                  break;
                case SELL:
                  System.out.println("Hedge fund SELL !!!!!!!!");
                  double volume = h.getVolume();
                  h.sell(volume);
                  break;
              }

            } else {
              System.out.println("Hedge fund " + h.getID() + " holds");
              h.hold();
            }
          }
      );

  public static Action<HedgeFundShort> updateStrategy =
      action(
          h -> {
            if (h.hasMessagesOfType(Messages.TrueValue.class)) {
              System.out.println("HF get true value");

              double trueValue = h.getMessageOfType(Messages.TrueValue.class).getBody();
              h.volume = trueValue * 0.3;

////              h.amountToSS = Math.abs(trueValue / 4);
//
//              if (h.isSSing && h.getGlobals().tickCount - h.tickStartSS < h.timeToStopSS) {
//                h.shouldTrade = true;
//                return; // expect to sell
//              }
//
//              if (!h.isSSing && trueValue >= h.priceToSecondSS) {
//                h.isSSing = true;
//                h.tickStartSS = h.getGlobals().tickCount;
//                h.shouldTrade = true;
//                return; // expect to further sell
//              }
//
//              if (trueValue <= h.targetPriceToClosePos) {
//                h.timeToReapProfit = true;
//                h.shouldTrade = true;
//                return; // buy
//              }
//
//              if (trueValue >= h.priceToStopLoss) {
//                h.shouldStopLoss = true;
//                h.shouldTrade = true;
//                return; // buy
//              }
//
//              h.isSSing = false; // re-set
//              h.shouldTrade = false;
//            }

            }
          });

  protected void hold() {
    buy(0);
  }

  protected float getMarketPrice() {
    return getMessageOfType(Messages.MarketPrice.class).getBody();
  }

  protected void buy(double volume) {
    getDoubleAccumulator("buys").add(volume);
    getLinks(HedgeFundLink.class).send(Messages.BuyOrderPlaced.class, volume);
    shares += volume;
  }

  protected void sell(double volume) {
    System.out.println("sell volume: " + volume);
    getDoubleAccumulator("sells").add(volume);
    getLinks(HedgeFundLink.class).send(Messages.SellOrderPlaced.class, volume);
    shares -= volume;
  }
}
