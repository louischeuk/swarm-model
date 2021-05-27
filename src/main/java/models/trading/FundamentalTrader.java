package models.trading;

import models.trading.Messages.InfluencerSocialNetworkOpinion;
import models.trading.Messages.SocialNetworkOpinion;
import simudyne.core.abm.Action;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

/* Fundamental(informed) trader: force to buy and sell at prices bounded by the intrinsic value */
public class FundamentalTrader extends Trader {

  @Variable
  public double intrinsicValue;

  @Variable // bounded between [-1, 1]
  public double opinion;

  @Variable // for update the opinion
  public double opinionThresh;

  private static Action<FundamentalTrader> action(
      SerializableConsumer<FundamentalTrader> consumer) {
    return Action.create(FundamentalTrader.class, consumer);
  }

  @Override
  protected void tradeStrategy() {

    System.out.println("Trader id: " + getID());
    System.out.println("-------------- fundamental trader strategy --------------");

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

  /* random walk - brownian motion - keep estimate */
  public static Action<FundamentalTrader> adjustIntrinsicValue =
      action(
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

//            System.out.println("Trader " + t.getID() + " updated intrinsic value");

//            t.adjustIntrinsicWithNewOpinion();

          });

  protected void reactMarketShock() {
    int shockPrice = getMessageOfType(Messages.MarketShock.class).getBody();
    intrinsicValue = getPrng().normal(shockPrice, getGlobals().stdDev).sample();
    System.out.println("New intrinsic value: " + intrinsicValue);
  }


  /* share opinion to the social network */
  public static Action<FundamentalTrader> shareOpinion =
      action(
          t -> {
            t.getLinks(Links.SocialNetworkLink.class).send(Messages.OpinionShared.class, t.opinion);
            System.out.println("Trader " + t.getID() + " sent opinion");
          });

  /* fetch the opinion from social network and update the self opinion accordingly */
  public static Action<FundamentalTrader> fetchAndAdjustOpinion =
      action(
          t -> {
            System.out.println("Trader ID " + t.getID() + " received opinion");
            t.adjustOpinionWithInfluencerOpinion();
            t.adjustOpinionWithTradersOpinions();
          });


  public void adjustOpinionWithInfluencerOpinion() {

    /* take opinion from influencer */

    if (hasMessageOfType(InfluencerSocialNetworkOpinion.class)) {
      double opinionsFromInfluencer =
          getMessageOfType(InfluencerSocialNetworkOpinion.class).getBody();

      System.out.println("WOWWWWWWWW Opinion from Elon Musk: " + opinionsFromInfluencer);

      double confidenceFactor = getPrng().uniform(0, 0.1).sample();
      opinion += (opinionsFromInfluencer - opinion) * confidenceFactor;
      System.out.println("opinion after Elon: " + opinion);
      fixOpinionBoundary();
    }

  }


  public void adjustOpinionWithTradersOpinions() {

    /* take opinion from other trader agents */

    double[] opinionsList = getMessageOfType(SocialNetworkOpinion.class).opinionList;

//    System.out.println("Opinion before update: " + t.opinion);
//    System.out.println("Opinion thresh : " + t.opinionThresh);

    int count = 0;
    for (double o : opinionsList) {
      if (getPrng().uniform(0, 1).sample() < opinionThresh) {
        count++;

//        double confidenceFactor = 1 / (t.getGlobals().k + (t.opinion - o));
        double confidenceFactor = 0.01 / (Math.abs(o - opinion) + 1);
//        System.out.println("Confidence Factor: " + confidenceFactor);

        opinion += (o - opinion) * confidenceFactor;
        fixOpinionBoundary();
      }
    }

    System.out.println(count + " opinions out of " + opinionsList.length + " considered");

//    System.out.println("Opinion after update: " + t.opinion);
  }


  public void fixOpinionBoundary() {
    opinion = (opinion < 0 && opinion < -1) ? -1 : opinion;
    opinion = (opinion > 0 && opinion > 1) ? 1 : opinion;
  }

  public static Action<FundamentalTrader> updateOpinionThreshold =
      action(
          t -> {
            if (t.getPrng().generator.nextInt(2) == 1) {
              t.opinionThresh = t.getPrng().uniform(0, 1).sample();
              System.out.println("updateOpinionThreshold action here");
            }
          });


  protected void adjustIntrinsicWithNewOpinion() {
    double opinionMultiple = 1 + (opinion / getGlobals().opinionFactor);
    System.out.println("opinion multiple: " + opinionMultiple);
    intrinsicValue *= opinionMultiple;
  }


}
