package models.trading;

import models.trading.Messages.MarketPrice;
import org.apache.commons.math3.exception.MathIllegalStateException;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public class Trader extends Agent<TradingModel.Globals> {

  public enum Type {Momentum, ZI, MI, Opinionated, Coordinated}

  ;
   /*
   Trader type:
   momentum: buy securities that are rising and sell them when they look to have peaked
   zero-intelligence trader: force to buy and sell at prices bounded by the intrinsic value
   minimal-intelligence trader: adapt to the environment is seen by some as a minimal intelligence
   opinionated: has the knowledge of Limit Order Book.
                        submits quote prices that vary according to its opinion
   coordinated: Reddit WSB
   */

  public Type type;

  @Variable
  public double intrinsicValue;

  @Variable
  public double wealth;

  @Variable
  public double shares = 0;

  @Variable
  public int timeSinceShort = -1;

  @Variable // after this shortDuration, you must cover your short positions
  public int shortDuration;

  @Variable
  public double marginAccount = 0;

  public boolean isBroke = false;

  @Variable // the total times of transaction of short selling at a moment cannot exceed a int
  public int numOfShortingSellInProcess = 0;


  @Override
  public void init() {
    intrinsicValue = getPrng().normal(getGlobals().marketPrice, getGlobals().sigma).sample();
    wealth = getPrng().exponential(100).sample();
    shortDuration = getPrng().generator.nextInt(getGlobals().shortSellDuration) + 1;
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

            if (Math.abs(priceDistortion) < t.intrinsicValue * 0.008) {
              System.out.println("Trader holds");

            } else if (priceDistortion > 0) {

              int buyVolume =
                  (int) Math.ceil(
                      t.getPrng().normal(priceDistortion, 0.15).sample()
//                      priceDistortion
                          * t.getGlobals().sensitivity
                          * t.getGlobals().weighting); // weight before buy / sell

              System.out.println("Amount shares to buy: " + buyVolume);

              t.handleWhenBuyShares(price, buyVolume);

            } else if (priceDistortion < 0) { // sell or short-sell

              int sellVolume =
                  (int) Math.ceil(Math.abs(
                      t.getPrng().normal(priceDistortion, 0.15).sample())
//                      priceDistortion)
                      * t.getGlobals().sensitivity
                      * t.getGlobals().weighting);

              System.out.println("Amount shares to sell: " + sellVolume);

              t.handleWhenSellShares(sellVolume);
            }

            if (t.timeSinceShort > -1) { // short selling
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

          t.intrinsicValue = p >= 0.55
              ? t.getPrng().normal(price, p).sample() * 0.995
              : t.getPrng().normal(price, 1 - p).sample() * 0.995;

          // best so far
//          t.intrinsicValue = p >= 0.55
//              ? t.getPrng().normal(price, p).sample() * 0.99
//              : t.getPrng().normal(price, 1-p).sample() * 0.99;

//          t.intrinsicValue = t.getPrng().normal(price, p).sample();

//          System.out.println("New intrinsic value: " + t.intrinsicValue);
        });
  }

  private void handleWhenBuyShares(double price, int sharesToBuy) {
    if (hasEnoughWealth(price * sharesToBuy)) {
      buy(sharesToBuy);

      if (shares >= 0 && timeSinceShort > -1) {
        // cover all the shorts position

        resetMarginAccount();
      }
    }
  }

  private void handleWhenSellShares(int sharesToSell) {
    if (hasEnoughShares(sharesToSell)) {
      sell(sharesToSell);
    } else {
      handleNotEnoughSharesToSell(sharesToSell);
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

  private void initiateMarginAccount(double sharesToShort) {
    marginAccount =
        (sharesToShort * getGlobals().marketPrice) * (1 + getGlobals().initialMarginRequirement);

    System.out.println("Margin account: " + marginAccount);
  }

  private boolean isShortSellAllowed(double sharesToSell) {
    if (numOfShortingSellInProcess >= 3) {
      System.out.println("Already shorted " + numOfShortingSellInProcess + " times, wait!");
      return false;
    }
    return isWealthMeetInitialMarginRequirement(sharesToSell);
  }

  private boolean isWealthMeetInitialMarginRequirement(double sharesToSell) {
    // example: with 10K investment, you need 5K cash at start
    if (shares == 0) {
      return wealth
          >= (sharesToSell * getGlobals().marketPrice * getGlobals().initialMarginRequirement);
    }

    // share < 0: (there are already some short positions)
    System.out.println("Hello shares here is " + shares);
    return wealth >= (
        (Math.abs(shares) + sharesToSell) * getGlobals().marketPrice
            * getGlobals().initialMarginRequirement);

  }

  private void handleDuringShortSelling() {
    if (isMarginCallTriggered()) {
      forceLiquidateShortPosition();
      System.out.println("Oh shit forced to liquidate!");
    }

    if (timeSinceShort++ > shortDuration) {
      coverShortPosition(Math.abs(shares));
    }
  }

  private void resetMarginAccount() {
    marginAccount = 0;
    timeSinceShort = -1;
    numOfShortingSellInProcess = 0;
  }

  private void forceLiquidateShortPosition() {
    coverShortPosition(Math.abs(shares));
  }

  private void coverShortPosition(double sharesToCover) {

    buy(sharesToCover);
    resetMarginAccount();

    System.out.println("Shorts Position covered lolll");

    if (wealth < 0) {
      System.out.println("wealth drops below -ve: you broke!");
      isBroke = true;
    }
  }

  private void buy(double amountToBuy) {

    if (shares < 0) {
      getLongAccumulator("coverShorts").add((long) amountToBuy);
      getLinks(Links.TradeLink.class).send(Messages.CoverShortPosOrderPlaced.class, amountToBuy);
    }

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
    if (shares < 0) {
      updateMarginAccount(sharesToShort); // update margin account with more shorts
    } else {
      initiateMarginAccount(sharesToShort);
    }

    getLongAccumulator("shorts").add((long) sharesToShort);
    getLinks(Links.TradeLink.class).send(Messages.ShortSellOrderPlaced.class, sharesToShort);

    System.out.println("shares of short: " + sharesToShort);

    sell(sharesToShort);

    timeSinceShort = 0;
    numOfShortingSellInProcess++;
    System.out.println("Num of short sell in process: " + numOfShortingSellInProcess);
  }

  private void updateMarginAccount(double sharesToShort) {
    marginAccount =
        (Math.abs(shares) * getGlobals().marketPrice + sharesToShort * getGlobals().marketPrice)
            * (1 + getGlobals().initialMarginRequirement);

    System.out.println("~~~~~~Margin account updated: " + marginAccount);
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
