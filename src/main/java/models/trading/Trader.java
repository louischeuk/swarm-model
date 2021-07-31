package models.trading;

import models.trading.Links.TradeLink;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public abstract class Trader extends Agent<TradingModel.Globals> {

  public enum Side {BUY, SELL}

  public enum Type {Noise, Fundamental, Momentum, Coordinated, MI, Opinionated}
   /*
   ------- Trader type -------
   Noise trader (uninformed): will randomly buy or sell
   Fundamental trader (informed): force to buy and sell at prices bounded by the intrinsic value
   Momentum: buy securities that are rising and sell them when they look to have peaked
   Coordinated: Reddit WSB - buy and strong hold
   minimal-intelligence trader: adapt to the environment is seen by some as a minimal intelligence
   opinionated: has the knowledge of Limit Order Book.
                        submits quote prices that vary according to its opinion
   */

  Type type; /* Trader type */

  public double wealth;

  @Variable
  public double shares = 0;

  int timeSinceShort = -1;

  // after this shortDuration, you must cover your short positions
  int shortDuration;

  double marginAccount = 0;

  boolean isBroke = false;

  // the total times of transaction of short selling at a moment cannot exceed a int
  int numShortingInProcess = 0;

  static double initialMarginRequirement = 0.5;

  static double maintenanceMargin = 0.3;

  /* ------------------- functions ------------------- */

  private static Action<Trader> action(SerializableConsumer<Trader> consumer) {
    return Action.create(Trader.class, consumer);
  }

  public static Action<Trader> submitLimitOrders =
      action(
          t -> {
            if (!t.isBroke) {
              System.out.println("Trader id: " + t.getID());

              double alpha = t.getAlpha();
              double p = t.getPrng().uniform(0, 1).sample();
              if (p < alpha) {
                double volume = t.getVolume();
                Side side = t.getSide();
                switch (side) {
                  case BUY:
                    t.handleWhenBuyShares(volume);
                    break;
                  case SELL:
                    t.handleWhenSellShares(volume);
                    break;
                }
              } else {
                System.out.println("Trader " + t.getID() + " holds");
                t.hold(); // only uncomment if there are only FT Traders
              }

              if (t.timeSinceShort > -1) { // short selling
                t.handleDuringShortSelling();
              }

            } else {
              System.out.println("this trader is broke!");
            }
          }
      );

  protected abstract double getAlpha();

  protected abstract Side getSide();

  protected abstract double getVolume();

  protected void hold() {
    buy(0);
  }

  protected void handleWhenBuyShares(double volume) {

    if (hasEnoughWealth(getMarketPrice() * volume)) {

      if (shares >= 0) {
        buy(volume);
      } else {
        if (shares + volume <= 0) { // volume < current short sell positions
          closeShortPos(volume);
        } else if (shares + volume > 0){ // volume > current short sell positions
          double vol = shares + volume;
          closeShortPos(Math.abs(shares));
          buy(vol);
        }
      }

      if (shares < 0) {
        updateMarginAccount(Math.abs(shares) - volume);
      } else {
        if (timeSinceShort > -1) {
          resetMarginAccount(); // cover all the shorts position
        }
      }

      if (wealth < 0) {
        System.out.println("wealth drops below -ve: you broke!");
        isBroke = true;
      }

    } else {
      System.out.println("Not enough money");
      noMoneyToTrade();
    }
  }

  private void noMoneyToTrade() {
    hold();
  }

  protected float getMarketPrice() {
    return getMessageOfType(Messages.MarketPrice.class).getBody();
  }

  protected void handleWhenSellShares(double volume) {
    if (hasEnoughShares(volume)) {
      sell(volume);
    } else {
      handleNotEnoughSharesToSell(volume);
    }
  }

  protected void handleNotEnoughSharesToSell(double sharesToSell) {
    if (shares <= 0) {
      if (isShortSellAllowed(sharesToSell)) {
        shortSell(sharesToSell);
      }
    } else if (shares > 0) {
      double vol = sharesToSell - shares;
      sell(shares);
      if (isShortSellAllowed(vol)) {
        shortSell(vol);
      }
    }
  }

  private void initiateMarginAccount(double sharesToShort) {
    marginAccount = sharesToShort * getMarketPrice() * (1 + initialMarginRequirement);
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
      return wealth >= (sharesToSell * getMarketPrice() * initialMarginRequirement);
    }

    // share < 0: (there are already some short positions)
    System.out.println("Hello short shares here is " + shares);
    return wealth >=
        ((Math.abs(shares) + sharesToSell) * getMarketPrice() * initialMarginRequirement);

  }

  protected void handleDuringShortSelling() {
    if (isMarginCallTriggered() && !hasEnoughWealthToMaintainMarginAccount()) {
      forceCloseShortPos();
      System.out.println("Oh shit being forced to liquidate!");
    }

    if (timeSinceShort++ > shortDuration) {
      closeShortPos(Math.abs(shares));
    }
  }

  protected void resetMarginAccount() {
    marginAccount = 0;
    timeSinceShort = -1;
    numShortingInProcess = 0;
  }

  protected void forceCloseShortPos() {
    closeShortPos(Math.abs(shares));
  }

  protected void closeShortPos(double volume) {

    System.out.println("Close pos: " + volume);
    getDoubleAccumulator("closeShorts").add(volume);
    getLinks(TradeLink.class).send(Messages.CloseShortPosOrderPlaced.class, volume);

    buy(volume);

    if (shares >= 0) {
      resetMarginAccount();
      System.out.println("Shorts Position got closed....");
    }
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

  protected boolean hasEnoughWealthToMaintainMarginAccount() {
    double totalValueOfShorts = Math.abs(shares) * getMarketPrice();
    double wealthRequiredInMarginAccount = marginAccount * maintenanceMargin;

    return wealth >= wealthRequiredInMarginAccount - (marginAccount - totalValueOfShorts)
        && wealth >= wealthRequiredInMarginAccount; // just double check
  }

  protected boolean isMarginCallTriggered() {
    double totalValueOfShorts = Math.abs(shares) * getMarketPrice();
    return ((marginAccount - totalValueOfShorts) / totalValueOfShorts) < maintenanceMargin;
    /*
      formula: if (Trader's money / value of all short position) x 100% < maintenance margin,
      then margin call is triggered
    */
  }

  protected void shortSell(double volume) {
    if (shares < 0) {
      updateMarginAccount(Math.abs(shares) + volume);
    } else {
      initiateMarginAccount(volume);
    }

    getDoubleAccumulator("shorts").add(volume);
    getLinks(TradeLink.class).send(Messages.ShortSellOrderPlaced.class, volume);

    System.out.println("shares of short: " + volume);

    sell(volume);

    if (timeSinceShort == -1) {
      timeSinceShort = 0;
    }

    numShortingInProcess++;
//    System.out.println("Num of short sell in process: " + numShortingInProcess);
  }

  protected void updateMarginAccount(double volume) {
    marginAccount = volume * getMarketPrice() * (1 + initialMarginRequirement);
//    System.out.println("~~~~~~Margin account updated: " + marginAccount);
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

  protected boolean hasEnoughShares(double volume) {
    return shares >= volume;
  }

  protected boolean hasEnoughWealth(double totalValueOfShares) {
    return wealth >= totalValueOfShares;
  }

}
