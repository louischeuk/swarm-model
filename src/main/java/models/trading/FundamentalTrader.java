package models.trading;

import models.trading.Links.SocialNetworkLink;
import models.trading.Messages.InfluencerSocialNetworkOpinion;
import simudyne.core.abm.Action;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

/* Fundamental(informed) trader: force to buy and sell at prices bounded by the intrinsic value */
public class FundamentalTrader extends Trader {

  @Variable
  public double intrinsicValue;

  @Variable
  public double intrinsicNoOpnDynamics;

  @Variable
  public double opinion; /*
                           opinion = zScore
                                   = N(0, 1) for v_i(t) = opinion * sd + V(t)
                           intention: to fixed the direction of intrinsic
                           will adjust with other opinions
                        */

  @Variable
  public double zScore; /* intrinsic Value without opinion for comparison */

  /* ------------------ functions definition -------------------------*/

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
    if ((getPrng().uniform(0, 1).sample() < Math.abs(alpha)) && alpha != 0) {

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
            }

//            System.out.println("Trader " + t.getID() + " prev intrinsic value: " + t.intrinsicValue);

            /*
               control set-up: absent of social network
               --- intrinsic value (v_i(t) for trader i, step t) without opinion ---
               v_i(t) = opinion * sd  + V(t)
               v_i(t) ~ N(V(t), sd) if opinion ~ N(0,1)
            */
            t.intrinsicNoOpnDynamics = t.zScore * t.getGlobals().stdDev + t.getGlobals().trueValue;
            t.intrinsicNoOpnDynamics = t.intrinsicNoOpnDynamics <= 0 ? 0 : t.intrinsicNoOpnDynamics;

            /* update intrinsic with social network */
            t.intrinsicValue = (t.opinion * t.getGlobals().stdDev) + t.getGlobals().trueValue;
            t.intrinsicValue = t.intrinsicValue <= 0 ? 0 : t.intrinsicValue;

//            System.out.println("Trader " + t.getID() + " new intrinsic value: " + t.intrinsicValue);
          });


  /* share opinion to the social network */
  public static Action<FundamentalTrader> shareOpinion =
      action(
          t -> {
            t.getLinks(SocialNetworkLink.class).send(Messages.TraderOpinionShared.class, t.opinion);
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

  /* take opinion from other trader agents */
  public void adjustOpinionWithTradersOpinions() {

    double[] opinionsList = getMessageOfType(Messages.SocialNetworkOpinion.class).opinionList;
    getDoubleAccumulator("opinions").add(opinion);

//    int count = 0;
    for (double o : opinionsList) {
      if (Math.abs(o - opinion) < getGlobals().vicinityRange) {

        opinion += (o - opinion) * getGlobals().gamma;

        /* dynamics confidence factor */
        // it doesnt work well because the opinions considered are still close to the self opinion,
        // so it converges super quickly
//        double gamma = 1 / (Math.abs(o - opinion) + 1);
//        double beta = 1 - gamma;
        /* opinion = opinion * selfConfidence + otherOpinion * ConfidenceToOther */
//        opinion = opinion * beta + o * gamma;
      }
    }
//    System.out.println(count + " opinions out of " + opinionsList.length + " opinions considered");
  }

  /* take opinion from influencer */
  public void adjustOpinionWithInfluencerOpinion() {

    if (hasMessageOfType(InfluencerSocialNetworkOpinion.class)) {
      double influencerOpinion = getMessageOfType(InfluencerSocialNetworkOpinion.class).getBody();

//      System.out.println("WOWWWWWWWW Opinion from Elon Musk: " + influencerOpinion);

//      if (opinion > 0) {
//        opinion += (influencerOpinion - opinion) * (getGlobals().gamma + 0.005);
//      }

      double confidenceFactor = (1 / (Math.abs(influencerOpinion - opinion) + 10));
      opinion += (influencerOpinion - opinion) * confidenceFactor;
      System.out.println("opinion after Elon: " + opinion);
    }

  }

}

