package models.swarming;

import models.swarming.Links.HedgeFundLink;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public class HedgeFundLong extends Agent<Globals> {

  public enum Side {BUY, SELL}

  Side side = Side.BUY;

  @Variable
  public double shares = 0;

  @Variable
  public float curPrice; // record the current price

  public double volume;

  public double priceToBuy = 20;

  public double priceToSell = 220;

  /* --------------- functions --------------- */

  public double getAlpha() {

    if (curPrice <= priceToBuy) {
      return 1.0d;
    }
    if (shares > 0 && curPrice >= priceToSell) {
      side = Side.SELL;
      return 1.0d;
    }
    return 0.0d;
  }

  public Side getSide() { return side; }

  protected double getVolume() { return volume; }

  /* ------------------- functions ------------------- */

  public static Action<HedgeFundLong> action(SerializableConsumer<HedgeFundLong> consumer) {
    return Action.create(HedgeFundLong.class, consumer);
  }

  public static Action<HedgeFundLong> submitOrders =
      action(
          h -> {
            System.out.println("Hedge fund long submits order");
            h.curPrice = h.getMarketPrice();
            double alpha = h.getAlpha();
            if (alpha == 1) {
              Side side = h.getSide();
              switch (side) {
                case BUY:
                  System.out.println("Hedge fund 2 BUY !!!!!!!!");
                  double volume = h.getVolume();
                  h.buy(volume);
                  break;
                case SELL:
                  System.out.println("Hedge fund 2 SELL !!!!!!!!");
                  h.sell(Math.abs(h.shares));
                  break;
              }

            } else {
              System.out.println("Hedge fund " + h.getID() + " holds");
              h.hold();
            }
          }
      );

  public static Action<HedgeFundLong> updateStrategy =
      action(
          h -> {
            if (h.hasMessagesOfType(Messages.TrueValue.class)) {
              System.out.println("HF22 get true value");

              double trueValue = h.getMessageOfType(Messages.TrueValue.class).getBody();
              h.volume = trueValue * 4;

//              if (!h.timeToBuy && trueValue <= h.priceToBuy) {
//                h.timeToBuy = true;
//                h.shouldTrade = true;
//                return;
//              }
//              if (h.timeToBuy && trueValue >= h.priceToSell) {
//                h.timeToSell = true;
//                h.shouldTrade = true;
//                return;
//              }
//
//              h.shouldTrade = false;
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
