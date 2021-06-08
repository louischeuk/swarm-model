package models.trading;

import models.trading.Links.TradeLink;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public abstract class Trader extends Agent<TradingModel.Globals> {


  public enum Type {Noise, Fundamental, Momentum, MI, Opinionated, Coordinated}

  ;
   /*
   ------- Trader type -------
   Noise trader (uninformed): will randomly buy or sell
   Fundamental trader (informed): force to buy and sell at prices bounded by the intrinsic value
   momentum: buy securities that are rising and sell them when they look to have peaked
   minimal-intelligence trader: adapt to the environment is seen by some as a minimal intelligence
   opinionated: has the knowledge of Limit Order Book.
                        submits quote prices that vary according to its opinion
   coordinated: Reddit WSB
   */

  public Type type; // Trader type

  public double wealth;

  @Variable
  public double shares = 0;

  public int timeSinceShort = -1;

  // after this shortDuration, you must cover your short positions
  public int shortDuration;

  public double marginAccount = 0;

  public boolean isBroke = false;

  // the total times of transaction of short selling at a moment cannot exceed a int
  public int numShortingInProcess = 0;

  /* ------------------- functions ------------------- */

  private static Action<Trader> action(SerializableConsumer<Trader> consumer) {
    return Action.create(Trader.class, consumer);
  }

  public static Action<Trader> processMarketPrice =
      action(
          t -> {
            if (!t.isBroke) {

              t.tradeStrategy();

              if (t.timeSinceShort > -1) { // short selling
                t.handleDuringShortSelling();
                System.out.println("you are short selling");
              }

            } else {
              System.out.println("this trader is broke!");
            }
          }
      );

  protected abstract void tradeStrategy();

  protected void hold() {
    buy(0);
    sell(0);
    System.out.println("Trader " + getID() + " holds");
  }

  protected void handleWhenBuyShares(int sharesToBuy) {

    if (hasEnoughWealth(getMarketPrice() * sharesToBuy)) {
      buy(sharesToBuy);

      if (shares < 0) {
        updateMarginAccount(Math.abs(shares) - sharesToBuy);
      } else {
        if (shares >= 0 && timeSinceShort > -1) {
          resetMarginAccount(); // cover all the shorts position
        }
      }

    }
  }

  protected double getMarketPrice() {
    return getMessageOfType(Messages.MarketPrice.class).getBody();
  }

  protected void handleWhenSellShares(int sharesToSell) {
    if (hasEnoughShares(sharesToSell)) {
      sell(sharesToSell);
    } else {
      handleNotEnoughSharesToSell(sharesToSell);
    }
  }

  protected void handleNotEnoughSharesToSell(double sharesToSell) {
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
        (sharesToShort * getMarketPrice()) * (1 + getGlobals().initialMarginRequirement);

//    System.out.println("Margin account: " + marginAccount);
  }

  protected boolean isShortSellAllowed(double sharesToSell) {
    if (numShortingInProcess >= getGlobals().maxShortingInProcess) {
      System.out.println("Already shorted " + numShortingInProcess + " times, wait!");
      return false;
    }
    return isWealthMeetInitialMarginRequirement(sharesToSell);
  }

  protected boolean isWealthMeetInitialMarginRequirement(double sharesToSell) {
    // example: with 10K investment, you need 5K cash at start
    if (shares == 0) {
      return wealth
          >= (sharesToSell * getMarketPrice() * getGlobals().initialMarginRequirement);
    }

    // share < 0: (there are already some short positions)
    System.out.println("Hello short shares here is " + shares);
    return wealth >= (
        (Math.abs(shares) + sharesToSell) * getMarketPrice()
            * getGlobals().initialMarginRequirement);

  }

  protected void handleDuringShortSelling() {
    if (isMarginCallTriggered() && !hasEnoughWealthToMaintainMarginAccount()) {
      forceLiquidateShortPosition();
      System.out.println("Oh shit forced to liquidate!");
    }

    if (timeSinceShort++ > shortDuration) {
      coverShortPosition(Math.abs(shares));
    }
  }

  protected void resetMarginAccount() {
    marginAccount = 0;
    timeSinceShort = -1;
    numShortingInProcess = 0;
  }

  protected void forceLiquidateShortPosition() {
    coverShortPosition(Math.abs(shares));
  }

  protected void coverShortPosition(double sharesToCover) {

    buy(sharesToCover);
    resetMarginAccount();

    System.out.println("Shorts Position covered lolll");

    if (wealth < 0) {
      System.out.println("wealth drops below -ve: you broke!");
      isBroke = true;
    }
  }

  protected void buy(double amountToBuy) {

    if (shares < 0) {
      getLongAccumulator("coverShorts").add((long) amountToBuy);
      getLinks(TradeLink.class).send(Messages.CoverShortPosOrderPlaced.class, amountToBuy);
    }

    getLongAccumulator("buys").add((long) amountToBuy);
    getLinks(TradeLink.class).send(Messages.BuyOrderPlaced.class, amountToBuy);

//    System.out.println("Previous wealth: " + wealth);

    wealth -= getMarketPrice() * amountToBuy;
    shares += amountToBuy;

//    System.out.println("Current wealth: " + wealth);
//    System.out.println("Current shares: " + shares);
  }

  protected boolean hasEnoughWealthToMaintainMarginAccount() {
    double totalValueOfShorts = Math.abs(shares) * getMarketPrice();
    double wealthRequiredInMarginAccount = marginAccount * getGlobals().maintenanceMargin;

    return wealth >= wealthRequiredInMarginAccount - (marginAccount - totalValueOfShorts)
        && wealth >= wealthRequiredInMarginAccount; // just double check
  }

  protected boolean isMarginCallTriggered() {
    double totalValueOfShorts = Math.abs(shares) * getMarketPrice();
    return ((marginAccount - totalValueOfShorts) / totalValueOfShorts)
        < getGlobals().maintenanceMargin;
    // formula: if (Trader's money / value of all short position) x 100% < maintenance margin,
    // then margin call is triggered

  }

  protected void shortSell(double sharesToShort) {
    if (shares < 0) {
      // update margin account with more shorts
      updateMarginAccount(Math.abs(shares) + sharesToShort);
    } else {
      initiateMarginAccount(sharesToShort);
    }

    getLongAccumulator("shorts").add((long) sharesToShort);
    getLinks(TradeLink.class).send(Messages.ShortSellOrderPlaced.class, sharesToShort);

    System.out.println("shares of short: " + sharesToShort);

    sell(sharesToShort);

    if (timeSinceShort == -1) {
      timeSinceShort = 0;
    }

    numShortingInProcess++;
//    System.out.println("Num of short sell in process: " + numShortingInProcess);
  }

  protected void updateMarginAccount(double sharesToShort) {
    marginAccount =
        sharesToShort * getMarketPrice() * (1 + getGlobals().initialMarginRequirement);

//    System.out.println("~~~~~~Margin account updated: " + marginAccount);
  }

  protected void sell(double sharesToSell) {

    getLongAccumulator("sells").add((long) sharesToSell);
    getLinks(TradeLink.class).send(Messages.SellOrderPlaced.class, sharesToSell);

//    System.out.println("Previous wealth: " + wealth);

    wealth += getMarketPrice() * sharesToSell;
    shares -= sharesToSell;

//    System.out.println("Current wealth: " + wealth);
//    System.out.println("Current shares: " + shares);
  }

  protected boolean hasEnoughShares(int amountToSell) {
    return shares >= amountToSell;
  }

  protected boolean hasEnoughWealth(double totalValueOfShares) {
    if (wealth >= totalValueOfShares) {
      return true;
    }

    System.out.println("Not enough money");
    getLinks(TradeLink.class).send(Messages.BuyOrderPlaced.class, 0);
    return false;
  }
}

