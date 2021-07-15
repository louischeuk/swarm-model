package models.trading;

import models.trading.Links.TradeLink;
import models.trading.TradingModel.Globals;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public class HedgeFund extends Agent<Globals>  {

  public enum Side {BUY, SELL}

  public double wealth;

  @Variable
  public int shares = 0;

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

  private static Action<HedgeFund> action(SerializableConsumer<HedgeFund> consumer) {
    return Action.create(HedgeFund.class, consumer);
  }

  public static Action<HedgeFund> submitLimitOrders =
      action(
          t -> {
            if (!t.isBroke) {

              double alpha = t.getAlpha();
              if ((t.getPrng().uniform(0, 1).sample() < alpha)) {
                Side side = t.getSide();
                int volume = t.getVolume();
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
                System.out.println("you are short selling");
              }

            } else {
              System.out.println("this trader is broke!");
            }
          }
      );

  protected double getAlpha() { return 1.0; }

  protected Side getSide() {return Side.SELL; }

  protected int getVolume() { return 1; }

  protected void hold() {
    buy(0);
//    System.out.println("Trader " + getID() + " holds");
  }

  protected void handleWhenBuyShares(int sharesToBuy) {

    if (hasEnoughWealth(getMarketPrice() * sharesToBuy)) {
      buy(sharesToBuy);

      if (shares < 0) {
        updateMarginAccount(Math.abs(shares) - sharesToBuy);
      } else {
        if (timeSinceShort > -1) {
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

  protected void handleNotEnoughSharesToSell(int sharesToSell) {
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
    marginAccount = (sharesToShort * getMarketPrice()) * (1 + initialMarginRequirement);
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
      return wealth >= (sharesToSell * getMarketPrice() * initialMarginRequirement);
    }

    // share < 0: (there are already some short positions)
    System.out.println("Hello short shares here is " + shares);
    return wealth >=
        ((Math.abs(shares) + sharesToSell) * getMarketPrice() * initialMarginRequirement);

  }

  protected void handleDuringShortSelling() {
    if (isMarginCallTriggered() && !hasEnoughWealthToMaintainMarginAccount()) {
      forceLiquidateShortPosition();
      System.out.println("Oh shit being forced to liquidate!");
    }

    if (timeSinceShort++ > shortDuration) {
      closeShortPosition(Math.abs(shares));
    }
  }

  protected void resetMarginAccount() {
    marginAccount = 0;
    timeSinceShort = -1;
    numShortingInProcess = 0;
  }

  protected void forceLiquidateShortPosition() {
    closeShortPosition(Math.abs(shares));
  }

  protected void closeShortPosition(int sharesToCover) {

    buy(sharesToCover);
    resetMarginAccount();

    System.out.println("Shorts Position got closed lolll");

    if (wealth < 0) {
      System.out.println("wealth drops below -ve: you broke!");
      isBroke = true;
    }
  }

  protected void buy(int amountToBuy) {

    if (amountToBuy != 0) {
      System.out.println("buy");
    }

    if (shares < 0) {
      getLongAccumulator("closeShorts").add(amountToBuy);
      getLinks(TradeLink.class).send(Messages.CloseShortPosOrderPlaced.class, amountToBuy);
    }

    getLongAccumulator("buys").add(amountToBuy);
    getLinks(TradeLink.class).send(Messages.BuyOrderPlaced.class, amountToBuy);

//    System.out.println("Previous wealth: " + wealth);

    wealth -= getMarketPrice() * amountToBuy;
    shares += amountToBuy;

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
    // formula: if (Trader's money / value of all short position) x 100% < maintenance margin,
    // then margin call is triggered

  }

  protected void shortSell(int sharesToShort) {
    if (shares < 0) {
      // update margin account with more shorts
      updateMarginAccount(Math.abs(shares) + sharesToShort);
    } else {
      initiateMarginAccount(sharesToShort);
    }

    getLongAccumulator("shorts").add(sharesToShort);
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
        sharesToShort * getMarketPrice() * (1 + initialMarginRequirement);

//    System.out.println("~~~~~~Margin account updated: " + marginAccount);
  }

  protected void sell(int sharesToSell) {
    System.out.println("sell");
    getLongAccumulator("sells").add(sharesToSell);
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
