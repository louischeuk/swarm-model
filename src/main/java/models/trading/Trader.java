package models.trading;

import models.trading.Links.TradeLink;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public abstract class Trader extends Agent<TradingModel.Globals> {

  public enum Side {BUY, SELL}

  public enum Type {Noise, Fundamental, Momentum, Coordinated}
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

  @Variable
  public double marginAccount = 0;

  boolean isBroke = false;

  static double initialMarginRequirement = 0.5;

  static double maintenanceMargin = 0.3;

  /* ------------------- functions ------------------- */

  private static Action<Trader> action(SerializableConsumer<Trader> consumer) {
    return Action.create(Trader.class, consumer);
  }

  public static Action<Trader> submitOrders =
      action(
          t -> {

            if (t.isBroke) {
              System.out.println("this trader is broke!");
              return;
            }

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
          }
      );

  protected abstract double getAlpha();

  protected abstract Side getSide();

  protected abstract double getVolume();

  protected void hold() { buy(0); }

  protected void handleWhenBuyShares(double volume) {

    if (hasEnoughWealth(getMarketPrice() * volume)) {
      if (shares >= 0) {
        buy(volume);

      } else {
        if (shares + volume <= 0) { // volume < current short sell positions
          closeShortPos(volume);

        } else if (shares + volume > 0) { // volume > current short sell positions
          double vol = shares + volume;
          closeShortPos(Math.abs(shares));
          buy(vol);
        }
      }

      updateTraderStatus();
      return;
    }

    System.out.println("Not enough money");
    noMoneyToTrade();
  }

  private void updateTraderStatus() {
    if (shares < 0) {
      handleDuringShortSelling();
    }
    if (shares < 0) {
      updateMarginAccount(Math.abs(shares));
    } else if (shares >= 0) {
      resetMarginAccount();
    }

    if (wealth < 0 && shares <= 0) {
      System.out.println("wealth drops below -ve: you broke!");
      isBroke = true;
    }
  }

  private void noMoneyToTrade() { hold(); }

  protected float getMarketPrice() {
    return getMessageOfType(Messages.MarketPrice.class).getBody();
  }

  protected void handleWhenSellShares(double volume) {
    if (hasEnoughShares(volume)) {
      sell(volume);
      return;
    }
    handleNotEnoughSharesToSell(volume);
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
      System.out.println("shares (should be 0 at the point): " + shares);
      System.out.println("Oh shit being forced to liquidate!");
    }
  }

  protected void resetMarginAccount() { marginAccount = 0; }

  protected void forceCloseShortPos() { closeShortPos(Math.abs(shares)); }

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
    return wealth >= totalValueOfShorts * maintenanceMargin;
  }

  protected boolean isMarginCallTriggered() {
    double totalValueOfShorts = Math.abs(shares) * getMarketPrice();
    return ((marginAccount - totalValueOfShorts) / totalValueOfShorts) < maintenanceMargin;
    /*
      formula: if (Trader's money / value of all short position) x 100% < maintenance margin,
      then margin call is triggered
      https://www.youtube.com/watch?v=4GA6WbQvn3I&ab_channel=MarketsKnow-How
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
  }

  protected void updateMarginAccount(double volume) {
    marginAccount = volume * getMarketPrice() * (1 + initialMarginRequirement);
    System.out.println("~~~~~~Margin account updated: " + marginAccount);
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
