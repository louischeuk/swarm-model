package models.trading;

import models.trading.Messages.MarketPrice;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public class Trader extends Agent<TradingModel.Globals> {

  @Variable
  public double intrinsicValue;

  @Variable
  public double wealth;

  @Variable
  public double shares = 0;

  public double sensitivity = 0.25;

  @Variable
  public double shortSellDuration = -1;

  @Variable
  public boolean isBroke = false;

  // trading decision: buy / sell / hold / short-sell

  @Override
  public void init() {
    intrinsicValue = getPrng().normal(getGlobals().marketPrice, getGlobals().sigma).sample();
    wealth = getPrng().exponential(1000).sample();
  }

  private static Action<Trader> action(SerializableConsumer<Trader> consumer) {
    return Action.create(Trader.class, consumer);
  }

  public static Action<Trader> processMarketPrice() {

    return action(
        t -> {

          if (t.isBroke) {
            System.out.println("this trader is broke!");

          } else {

            double price = t.getGlobals().marketPrice;
            double priceDistortion = t.intrinsicValue - price;

            System.out.println("Intrinsic: " + t.intrinsicValue);
            System.out.println("market price: " + price);

            if (priceDistortion > 0) {

              int sharesToBuy = (int) Math.ceil(priceDistortion * t.sensitivity);
//                * trader.getGlobals().weighting); // weight before buy / sell

              System.out.println("Amount to buy:" + sharesToBuy);

              if (t.hasEnoughWealth(price * sharesToBuy)) {
                t.buy(sharesToBuy);
              }

            } else if (priceDistortion < 0) { // sell or short-sell

              int sharesToSell = (int) Math.ceil(Math.abs(priceDistortion) * t.sensitivity);
//                * trader.getGlobals().weighting);
              System.out.println("Amount to sell:" + sharesToSell);

              if (t.hasEnoughStock(sharesToSell)) {
                t.sell(sharesToSell);
              } else {
                t.handleNotEnoughSharesToSell(sharesToSell);
              }
            }

            if (t.shortSellDuration > -1) {  // short-selling
              if (t.shortSellDuration++ > t.getGlobals().shortSellExpireDay) {
                t.coverShortPosition(t.shares);
              }
            }
          }
        }
    );
  }

  public static Action<Trader> adjustIntrinsicValue() {
    return action(
        t -> {
          double price = t.getMessageOfType(MarketPrice.class).getBody();
          System.out.println("Previous intrinsic: " + t.intrinsicValue);

          if (t.intrinsicValue < price) {
            t.intrinsicValue += t.getPrng().gaussian(0, 1).sample();
          } else {
            t.intrinsicValue -= t.getPrng().gaussian(0, 1).sample();
          }
          System.out.println("New intrinsic value: " + t.intrinsicValue);

        });
  }

  private void handleNotEnoughSharesToSell(double sharesToSell) {
    if (shares > 0) {
      sell(shares);
      shortSell(sharesToSell - shares);
    } else {
      shortSell(sharesToSell);
    }
  }

  private void buy(double amountToBuy) {
    getLongAccumulator("buys").add((long) amountToBuy);
    getLinks(Links.TradeLink.class).send(Messages.BuyOrderPlaced.class, amountToBuy);

    System.out.println("Previous wealth:" + wealth);

    wealth -= getGlobals().marketPrice * amountToBuy;
    shares += amountToBuy;

    System.out.println("Cur wealth:" + wealth);
    System.out.println("Cur stock:" + shares);
  }

  private void sell(double amountToSell) {

    getLongAccumulator("sells").add((long) amountToSell);
    getLinks(Links.TradeLink.class).send(Messages.SellOrderPlaced.class, amountToSell);

    System.out.println("Previous wealth:" + wealth);

    wealth += getGlobals().marketPrice * amountToSell;
    shares -= amountToSell;

    System.out.println("Cur wealth:" + wealth);
    System.out.println("Cur stock:" + shares);
  }

  private void coverShortPosition(double amountToCover) {
    getLongAccumulator("buys").add((long) amountToCover);
    getLinks(Links.TradeLink.class).send(Messages.BuyOrderPlaced.class, amountToCover);

    wealth -= getGlobals().marketPrice * amountToCover;
    shares = 0;
    shortSellDuration = -1; // reset

    if (wealth < 0) {
      System.out.println("wealth becomes -ve: you broke!");
      isBroke = true;
    }
  }

  private void shortSell(double amountToSell) {
    System.out.println("short sell!!!!!!!");
    sell(amountToSell);
    if (shortSellDuration == -1) {
      shortSellDuration = 0;
    }
  }

  private boolean hasEnoughStock(int amountToSell) {
    return shares >= amountToSell;
  }

  private boolean hasEnoughWealth(double totalPriceToPay) {
    return wealth >= totalPriceToPay;
  }

//  public static Action<Trader> createNewLink() {
//    Action.create(Trader.class, a -> {
//      a.getMessagesOfType(Messages.NewNeighborMessage.class).forEach(msg -> {
//        a.addLink(targetID, Links.NeighborTrader.class);
//      });
//    });
//  }
//}

}
