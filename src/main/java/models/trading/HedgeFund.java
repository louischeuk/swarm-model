package models.trading;

import models.trading.Links.TradeLink;
import models.trading.TradingModel.Globals;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public class HedgeFund extends Agent<Globals> {

  public enum Side {BUY, SELL}

  public Side side = Side.SELL;

  public double wealth;

  @Variable
  public int shares = 0;
  public int tickToFirstShortSell = 30;
  public int count = 0;
  public double amountToShortSell;
  public float price;
  public float curPrice;

  /* ------------------- functions ------------------- */

  public static Action<HedgeFund> action(SerializableConsumer<HedgeFund> consumer) {
    return Action.create(HedgeFund.class, consumer);
  }

  public static Action<HedgeFund> submitOrders =
      action(
          h -> {

            h.curPrice = h.getMarketPrice();

            double alpha = h.getAlpha();
            if (alpha == 1) {
              Side side = h.getSide();
              double volume = h.getVolume();
              switch (side) {
                case BUY:
                  h.buy(volume);
                  break;
                case SELL:
                  h.sell(volume);
                  h.price = h.getMarketPrice();
                  break;
              }

            } else {
              System.out.println("Hedge fund " + h.getID() + " holds");
              h.hold(); // only uncomment if there are only FT Traders
            }
          }
      );

  public static Action<HedgeFund> updateSide =
      action(
          h -> {
            if (h.curPrice / h.price == 3) {
              h.side = Side.BUY;
            }
          }
      );

  public double getAlpha() {
    System.out.println("Portion: " + Math.floor(curPrice / price));
    if (++count == tickToFirstShortSell || Math.floor(curPrice / price) == 2) {
      return 1;
    }
    return 0;
  }

  public Side getSide() { return side; }

  protected double getVolume() { return amountToShortSell; }

  protected void hold() { buy(0); }

  protected float getMarketPrice() {
    return getMessageOfType(Messages.MarketPrice.class).getBody();
  }

  protected void buy(double volume) {
    if (volume != 0) {
      System.out.println("buy volume: " + volume);
    }
    getDoubleAccumulator("buys").add(volume);
    getLinks(TradeLink.class).send(Messages.BuyOrderPlaced.class, volume);

//    System.out.println("Previous wealth: " + wealth);
    wealth -= getMarketPrice() * volume;
    shares += volume;
//    System.out.println("Current wealth: " + wealth);
//    System.out.println("Current shares: " + shares);
  }

  protected void sell(double volume) {
    System.out.println("sell volume: " + volume);
    getDoubleAccumulator("sells").add(volume);
    getLinks(TradeLink.class).send(Messages.SellOrderPlaced.class, volume);

//    System.out.println("Previous wealth: " + wealth);
    wealth += getMarketPrice() * volume;
    shares -= volume;
//    System.out.println("Current wealth: " + wealth);
//    System.out.println("Current shares: " + shares);
  }
}
