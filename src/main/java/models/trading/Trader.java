package models.trading;

import models.trading.Messages.MarketPrice;
import scala.concurrent.java8.FuturesConvertersImpl.P;
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

  @Variable
  public int timeSinceShortSell = -1;

  @Variable
  public int selfShortSellDuration;

  @Variable
  public boolean isBroke = false;

  @Variable
  public double marginAccount = 0;


  @Override
  public void init() {
    intrinsicValue = getPrng().normal(getGlobals().marketPrice, getGlobals().sigma).sample();
    wealth = getPrng().exponential(100).sample();
    selfShortSellDuration = getPrng().generator.nextInt(getGlobals().shortSellDuration) + 1;

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

              int sharesToBuy = (int) Math.ceil(priceDistortion * t.getGlobals().sensitivity
                  * t.getGlobals().weighting); // weight before buy / sell
              System.out.println("Amount shares to buy: " + sharesToBuy);

              t.handleWhenBuyShares(price, sharesToBuy);

            } else if (priceDistortion < 0) { // sell or short-sell

              int sharesToSell = (int) Math.ceil
                  (Math.abs(priceDistortion) * t.getGlobals().sensitivity
                      * t.getGlobals().weighting);
              System.out.println("Amount shares to sell: " + sharesToSell);

              t.handleWhenSellShares(sharesToSell);
            }

            if (t.timeSinceShortSell > -1) { // short selling
              t.handleDuringShortSelling();
            }
          }
        }
    );
  }


  // random walk - brownian motion - keep estimate
  public static Action<Trader> adjustIntrinsicValue() {
    return action(
        t -> {
          double price = t.getMessageOfType(MarketPrice.class).getBody();

//          System.out.println("Previous intrinsic: " + t.intrinsicValue);
          double p = t.getPrng().uniform(0, 1).sample();

          t.intrinsicValue = t.getPrng().normal(price, p).sample();
//          System.out.println("New intrinsic value: " + t.intrinsicValue);
        });
  }

  private void handleWhenBuyShares(double price, int sharesToBuy) {
    if (hasEnoughWealth(price * sharesToBuy)) {
      buy(sharesToBuy);

      if (shares >= 0 && timeSinceShortSell > -1) {
        // cover all the shorts position
        resetMarginAccount();
      }
    }
  }

  private void handleWhenSellShares(int sharesToSell) {
    if (hasEnoughShares(sharesToSell)) {
      sell(sharesToSell);
    } else if (shares >= 0) {
      handleNotEnoughSharesToSell(sharesToSell);
    } else {
      // cannot short more there is short position
      System.out.println("Short selling, waiting until short positions are covered");
    }
  }

  private void handleNotEnoughSharesToSell(double sharesToSell) {
    if (shares > 0) { // partly sell and partly short-sell
      sell(shares);
      if (isShortSellAllowed(sharesToSell - shares)) {
        shortSell(sharesToSell - shares);
      }
    } else {
      if (isShortSellAllowed(sharesToSell)) {
        shortSell(sharesToSell);
      }
    }
  }

  private void handleDuringShortSelling() {
    if (isMarginCallTriggered()) {
      forceLiquidateShortPosition();
      System.out.println("Oh shit forced to liquidate!");
    }

    if (timeSinceShortSell++ > selfShortSellDuration) {
      coverShortPosition(Math.abs(shares));
    }
  }

  private void resetMarginAccount() {
    marginAccount = 0;
    timeSinceShortSell = -1;
  }

  private void forceLiquidateShortPosition() {
    coverShortPosition(Math.abs(shares));
  }

  private void coverShortPosition(double sharesToCover) {
    buy(sharesToCover);
    resetMarginAccount();

    if (wealth < 0) {
      System.out.println("wealth drops below -ve: you broke!");
      isBroke = true;
    }
  }

  private void buy(double amountToBuy) {
    getLongAccumulator("buys").add((long) amountToBuy);
    getLinks(Links.TradeLink.class).send(Messages.BuyOrderPlaced.class, amountToBuy);

//    System.out.println("Previous wealth: " + wealth);

    wealth -= getGlobals().marketPrice * amountToBuy;
    shares += amountToBuy;

//    System.out.println("Current wealth: " + wealth);
//    System.out.println("Current shares: " + shares);
  }

  private boolean isMarginCallTriggered() {
    double totalValueOfShorts = Math.abs(shares) * getGlobals().marketPrice;
    return ((marginAccount - totalValueOfShorts) / totalValueOfShorts)
        < getGlobals().maintenanceMargin;
    // formula: if (Trader's money / value of all short position) x 100% < maintenance margin,
    // then margin call is triggered

  }


  private void shortSell(double sharesToShort) {

    initiateMarginAccount(sharesToShort);

    getLongAccumulator("shorts").add((long) sharesToShort);
    getLinks(Links.TradeLink.class).send(Messages.ShortSellOrderPlaced.class, sharesToShort);

    System.out.println("shares of short: " + sharesToShort);

    sell(sharesToShort);

    if (timeSinceShortSell == -1) {
      timeSinceShortSell = 0;
    }
  }

  private void sell(double sharesToSell) {

    getLongAccumulator("sells").add((long) sharesToSell);
    getLinks(Links.TradeLink.class).send(Messages.SellOrderPlaced.class, sharesToSell);

//    System.out.println("Previous wealth: " + wealth);

    wealth += getGlobals().marketPrice * sharesToSell;
    shares -= sharesToSell;

//    System.out.println("Current wealth: " + wealth);
//    System.out.println("Current shares: " + shares);
  }

  private void initiateMarginAccount(double sharesToShort) {
    marginAccount =
        (sharesToShort * getGlobals().marketPrice) * (1 + getGlobals().initialMarginRequirement);

    System.out.println("Margin account: " + marginAccount);
  }

  private boolean isShortSellAllowed(double sharesToSell) {
    return isWealthMeetInitialMarginRequirement(sharesToSell);
  }

  private boolean isWealthMeetInitialMarginRequirement(double sharesToSell) {
    return wealth
        >= (sharesToSell * getGlobals().marketPrice * getGlobals().initialMarginRequirement);
    // example: with 10K investment, you need 5K cash at start
  }

  private boolean hasEnoughShares(int amountToSell) {
    return shares >= amountToSell;
  }

  private boolean hasEnoughWealth(double totalValueOfShares) {
    if (wealth >= totalValueOfShares) {
      return true;
    }

    System.out.println("Not enough money");
    getLinks(Links.TradeLink.class).send(Messages.BuyOrderPlaced.class, 0);
    return false;
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
