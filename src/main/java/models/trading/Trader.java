package models.trading;

import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;
import simudyne.core.graph.Message.Double;

public class Trader extends Agent<TradingModel.Globals> {

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

  // the total times of transaction of short selling at a moment cannot exceed a int
  public int numShortingInProcess = 0;

  @Variable // a real-number between [-1, 1]
  public double opinion;

  @Variable // for update the opinion
  public double opinionThresh;


  private static Action<Trader> action(SerializableConsumer<Trader> consumer) {
    return Action.create(Trader.class, consumer);
  }

  public static Action<Trader> processMarketPrice =
      Action.create(Trader.class,
          t -> {
            if (!t.isBroke) {

              switch (t.type) {
                case Fundamental:
                  t.fundamentalTraderStrategy();
                  break;
                case Noise:
                  t.noiseTraderStrategy();
                  break;
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

  private void fundamentalTraderStrategy() {

    System.out.println("fundamental trader strategy - implementation as usual");

    double price = getGlobals().marketPrice;
    double priceDistortion = intrinsicValue - price;

    System.out.println("Intrinsic: " + intrinsicValue);
    System.out.println("market price: " + getGlobals().marketPrice);

    double alpha = priceDistortion * getGlobals().sensitivity;

    // if U(0,1) < alpha: buy / sell else hold
    if (getPrng().uniform(0, 1).sample() < Math.abs(alpha)
        && alpha != 0) {

      int volume = (int) Math.ceil(Math.abs(alpha));

      if (alpha > 0) {        // buy
        System.out.println("Amount shares to buy: " + volume);
        handleWhenBuyShares(volume);

      } else if (alpha < 0) { // sell
        System.out.println("Amount shares to sell: " + volume);
        handleWhenSellShares(volume);
      }

    } else {
      hold();
    }
  }

  private void noiseTraderStrategy() {
    System.out.println("noise trader strategy - no implementation yet");
  }

  private void hold() {
    buy(0);
    sell(0);
    System.out.println("Trader holds");
  }

  // random walk - brownian motion - keep estimate
  public static Action<Trader> adjustIntrinsicValue =
      Action.create(Trader.class,
          t -> {
            if (t.hasMessageOfType(Messages.MarketShock.class)) {
              System.out.println("Market shock is triggered!!!!!!!!!!!!!!");
              t.reactMarketShock();
            }

            int stdDev = 2;
            double weighting = 0.01;
            double step = Math.abs(t.getPrng()
                .gaussian(t.intrinsicValue * weighting, stdDev)
                .sample());

            t.intrinsicValue = t.getPrng().uniform(0, 1).sample() >= 0.5
                ? t.intrinsicValue + step
                : t.intrinsicValue - step;

            t.intrinsicValue = t.intrinsicValue <= 0 ? 0 : t.intrinsicValue;

//            t.adjustIntrinsicWithOpinion();

          });

  private void adjustIntrinsicWithOpinion() {
    double opinionMultiple = 1 + (opinion / getGlobals().opinionFactor);
//    System.out.println("opinion multiple: " + opinionMultiple);
    intrinsicValue *= opinionMultiple;
  }


  // send opinion to other trader agents
  public static Action<Trader> sendOpinion =
      Action.create(Trader.class, t ->
          t.getLinks(Links.SocialMediaLink.class).send(Messages.Opinion.class, t.opinion));


  public static Action<Trader> updateOpinion =
      Action.create(Trader.class, t -> {

        double[] opinionsList = t.getMessagesOfType(Messages.Opinion.class).stream()
            .mapToDouble(Double::getBody).toArray();

//        System.out.println("Opinion before update: " + t.opinion);

//        System.out.println("Opinion thresh : " + t.opinionThresh);

        int count = 0;
        for (double o : opinionsList) {
          if (t.getPrng().uniform(0, 1).sample() < t.opinionThresh) {
            count++;
            t.opinion += o * t.getGlobals().confidenceFactor;
          }
        }

//        System.out.println(count + " opinions considered");

//        System.out.println("Opinion after update: " + t.opinion);
      });


  public static Action<Trader> updateOpinionThreshold =
      Action.create(Trader.class, t -> {

        if (t.getPrng().generator.nextInt(2) == 1) {
          t.opinionThresh = t.getPrng().uniform(0, 1).sample();
//          System.out.println("updateOpinionThreshold action here");
        }
      });


  public void reactMarketShock() {
    int shockPrice = getMessageOfType(Messages.MarketShock.class).getBody();
    intrinsicValue = getPrng().normal(shockPrice, getGlobals().stdDev).sample();
    System.out.println("New intrinsic value: " + intrinsicValue);
  }

  private void handleWhenBuyShares(int sharesToBuy) {
    if (hasEnoughWealth(getGlobals().marketPrice * sharesToBuy)) {
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

//    System.out.println("Margin account: " + marginAccount);
  }

  private boolean isShortSellAllowed(double sharesToSell) {
    if (numShortingInProcess >= getGlobals().maxShortingInProcess) {
      System.out.println("Already shorted " + numShortingInProcess + " times, wait!");
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
    System.out.println("Hello short shares here is " + shares);
    return wealth >= (
        (Math.abs(shares) + sharesToSell) * getGlobals().marketPrice
            * getGlobals().initialMarginRequirement);

  }

  private void handleDuringShortSelling() {
    if (isMarginCallTriggered() && !hasEnoughWealthToMaintainMarginAccount()) {
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
    numShortingInProcess = 0;
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

  private boolean hasEnoughWealthToMaintainMarginAccount() {
    double totalValueOfShorts = Math.abs(shares) * getGlobals().marketPrice;
    double wealthRequiredInMarginAccount = marginAccount * getGlobals().maintenanceMargin;

    return wealth >= wealthRequiredInMarginAccount - (marginAccount - totalValueOfShorts)
        && wealth >= wealthRequiredInMarginAccount; // just double check
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
      // update margin account with more shorts
      updateMarginAccount(Math.abs(shares) + sharesToShort);
    } else {
      initiateMarginAccount(sharesToShort);
    }

    getLongAccumulator("shorts").add((long) sharesToShort);
    getLinks(Links.TradeLink.class).send(Messages.ShortSellOrderPlaced.class, sharesToShort);

    System.out.println("shares of short: " + sharesToShort);

    sell(sharesToShort);

    if (timeSinceShort == -1) {
      timeSinceShort = 0;
    }

    numShortingInProcess++;
//    System.out.println("Num of short sell in process: " + numShortingInProcess);
  }

  private void updateMarginAccount(double sharesToShort) {
    marginAccount =
        sharesToShort * getGlobals().marketPrice * (1 + getGlobals().initialMarginRequirement);

//    System.out.println("~~~~~~Margin account updated: " + marginAccount);
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
}

