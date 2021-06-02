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
  public double opinion; /*
                           opinion = zScore
                           N(0, 1) for v_i(t) = zScore * sd + V(t)
                           intention: to fixed the direction of intrinsic
                           will adjust with the opinion
                        */

  public double zScore; /*
                           opinion = zScore
                           N(0, 1) for v_i(t) = zScore * sd + V(t)
                           intention: to fixed the direction of intrinsic
                           will adjust with the opinion
                        */

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
            }

            /*
               --- intrinsic value (v_i(t) for trader i, step t) without opinion ---
               v_i(t) = z_score * sd  + V(t)
               v_i(t) ~ N(V(t), sd) if z_score ~ N(0,1)

                --- Update intrinsic value with opinion ---

                v_i(t) = (z_score + opinion) * sd  + V(t)

                The simplest model of opinion, with no network,
                is the z_score (I think the value ~N(V(t), sd),
                but I am pessimistic and my z_score is -1 and I underestimate V(t) by 1 sd.
                On my own, based only on my opinion, my v(t) is based on the z_score.
                Now I see the v(t) of my neighbour or the opinion of my neighbour
                and this might change my v(t).
            */

            System.out.println("Trader " + t.getID() + " prev intrinsic value: " + t.intrinsicValue);

            t.intrinsicValue =
                (t.zScore) * t.getGlobals().stdDev + t.getGlobals().trueValue;

            // opinion might be added separately

            t.intrinsicValue = t.intrinsicValue <= 0 ? 0 : t.intrinsicValue;

            System.out.println("Trader " + t.getID() + " new intrinsic value: " + t.intrinsicValue);
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


  public void adjustOpinionWithInfluencerOpinion() {

    /* take opinion from influencer */

    if (hasMessageOfType(InfluencerSocialNetworkOpinion.class)) {
      double influencerOpinion =
          getMessageOfType(InfluencerSocialNetworkOpinion.class).getBody();

      System.out.println("WOWWWWWWWW Opinion from Elon Musk: " + influencerOpinion);

      double influencerEffect = getPrng().uniform(0, 1).sample();

      double confidenceFactor =
          (1 / (Math.abs(influencerOpinion - opinion) + 1) + influencerEffect);

      // upper-bound of confidence factor: 1
      opinion += (influencerOpinion - opinion) * Math.min(1, confidenceFactor);
//      System.out.println("opinion after Elon: " + opinion);
      fixOpinionBoundary();
    }

  }


  public void adjustOpinionWithTradersOpinions() {

    /* take opinion from other trader agents */

    double[] opinionsList = getMessageOfType(Messages.SocialNetworkOpinion.class).opinionList;

//    System.out.println("Opinion before update: " + t.opinion);

    for (double o : opinionsList) {
//        double confidenceFactor = 1 / (Math.abs(o - opinion) + 1);
      double confidenceFactor = 0.4; // hard coded for now

//        System.out.println("Confidence Factor: " + confidenceFactor);

      opinion += (o - opinion) * confidenceFactor;
      fixOpinionBoundary();
  }

//    System.out.println(opinionsList.length + " opinions considered");

//    System.out.println("Opinion after update: " + t.opinion);
}


  public void fixOpinionBoundary() {
    opinion = (opinion < 0 && opinion < -1) ? -1 : opinion;
    opinion = (opinion > 0 && opinion > 1) ? 1 : opinion;
  }

}
