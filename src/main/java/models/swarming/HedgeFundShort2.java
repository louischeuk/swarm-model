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
public class HedgeFundShort2 extends Agent<Globals> {


  private boolean brought = false;

  public enum Side {BUY, SELL}

  @Variable
  public double shares = 0;

  public float curPrice; // record the current price

  @Variable
  public boolean isLeftMarket = false;

  /* -------------------- new --------------------------- */

  @Variable
  public boolean shouldTrade = false;

  // short sell for period 1
  public double amountToSS = 0; // decide dynamically

  @Variable
  public double tickStartSS = 0; //

  @Variable
  public boolean isSSing = false;

  public double timeToStopSS = 2;

  // expected time to cover pos
  public double targetPriceToClosePos = 50;

  public boolean timeToReapProfit = false;

  // short sell for period 2 (trigger: when a price is reached)
  public double priceToSecondSS = 200;

  // cover all the pos (trigger: when a price is reached) -> short squeeze
  public boolean shouldStopLoss = false;

  public double priceToStopLoss = 90; // due to short squeeze

  public double priceToFirstSS = 30; ////////////////////////////////



/*
  then at peak high, halt trade
  -> probability to trade change to 0.5 /
  -> only allow sell not buy
  -> limit amount of buy
 */


  public double getAlpha() { return shouldTrade ? 1 : 0; }

  public Side getSide() {
    return (timeToReapProfit || shouldStopLoss) ? Side.BUY : Side.SELL;
  }

  protected double getVolume() { return amountToSS; }

  /* ------------------- functions ------------------- */

  public static Action<HedgeFundShort2> action(SerializableConsumer<HedgeFundShort2> consumer) {
    return Action.create(HedgeFundShort2.class, consumer);
  }

  public static Action<HedgeFundShort2> submitOrders =
      action(
          h -> {
//            if (h.isLeftMarket) {
//              return;
//            }

            System.out.println("Hedge fund 2 submits order");
            h.curPrice = h.getMarketPrice();
            double alpha = h.getAlpha();
            if (alpha == 1) {
              Side side = h.getSide();
              switch (side) {

                case BUY:
                  System.out.println("Hedge fund 2 BUY !!!!!!!!");
                  h.buy(Math.abs(h.shares)); // buy back all
                  break;
                case SELL:
                  System.out.println("Hedge fund 2 SELL !!!!!!!!");
                  double volume = h.getVolume();
                  h.sell(Math.abs(volume));
                  break;
              }

            } else {
              System.out.println("Hedge fund " + h.getID() + " holds");
              h.hold();
            }
          }
      );

  public static Action<HedgeFundShort2> updateStrategy =
      action(
          h -> {
            if (h.hasMessagesOfType(Messages.TrueValue.class)) {
              System.out.println("HF2 get true value");

              double trueValue = h.getMessageOfType(Messages.TrueValue.class).getBody();
              System.out.println("true value received: " + trueValue);

              h.amountToSS = Math.abs(trueValue / 6);

              if (h.isSSing && h.getGlobals().tickCount - h.tickStartSS < h.timeToStopSS) {
                h.shouldTrade = true;
                return; // expect to sell
              }

              if (!h.isSSing && trueValue >= h.priceToSecondSS) {
                h.isSSing = true;
                h.tickStartSS = h.getGlobals().tickCount;
                h.shouldTrade = true;
                return; // expect to further sell
              }

              if (!h.brought && h.shares != 0 && trueValue <= h.targetPriceToClosePos) {
                h.timeToReapProfit = true;
                h.brought = true;
                h.shouldTrade = true;
                return; // buy
              }

//              if (trueValue >= h.priceToStopLoss) {
//                h.shouldStopLoss = true;
//                h.shouldTrade = true;
//                return; // buy
//              }

              h.isSSing = false; // re-set
              h.shouldTrade = false;
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
