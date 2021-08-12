package models.trading;

import models.trading.Links.HedgeFundLink;
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
//TODO: Package was not being imported properly in line below - previously was 'Agent<Globals>'
public class HedgeFund extends Agent<Globals> {

  public enum Side {BUY, SELL}

  @Variable
  public double shares = 0;

  public float priceAtFirstShort = -1; // record the price where it first short sell

  public float curPrice; // record the current price

  public boolean secondShortSell = false; // becomes true when HF short sell the 2nd time

  public boolean isLeftMarket = false;

  public double ratioToSecondSHortSell = 2;

  public double ratioToClosePos = 4;

  /* ------------------- functions ------------------- */

  public static Action<HedgeFund> action(SerializableConsumer<HedgeFund> consumer) {
    return Action.create(HedgeFund.class, consumer);
  }

  public static Action<HedgeFund> submitOrders =
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
                  h.buy(Math.abs(h.shares));
                  h.isLeftMarket = true;
                  break;
                case SELL:
                  h.sell(h.getVolume());
                  if (h.priceAtFirstShort == -1) {
                    h.priceAtFirstShort = h.curPrice;
                  }
                  break;
              }

            } else {
              System.out.println("Hedge fund " + h.getID() + " holds");
              h.hold();
            }
          }
      );


  public double getAlpha() {
    System.out.println("Portion: " + Math.floor(curPrice / priceAtFirstShort));
    if (getGlobals().tickCount == getGlobals().tickHFFirstShortSell ||
        getSide() == Side.BUY) {
      return 1.0d;
    }
    if (!secondShortSell && Math.floor(curPrice / priceAtFirstShort) == ratioToSecondSHortSell) {
      secondShortSell = true;
      return 1.0d;
    }
    return 0.0d;
  }

  public Side getSide() {
    if (secondShortSell && Math.floor(curPrice / priceAtFirstShort) == ratioToClosePos) {
      System.out.println("side changed to buy");
      return Side.BUY;
    }
    return Side.SELL;
  }

  protected double getVolume() {
    if (Math.floor(curPrice / priceAtFirstShort) == 2) {
      return getGlobals().amountToShortSellHF * 2;
    }
    return getGlobals().amountToShortSellHF;
  }

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
