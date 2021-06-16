package models.trading;

import models.trading.Links.SocialNetworkLink;
import models.trading.Messages.InfluencerSocialNetworkOpinion;
import simudyne.core.abm.Action;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public class MomentumTrader extends Trader {

  @Variable
  public double opinion;

  @Variable
  public double momentum = 0.0;

  public float lastMarketPrice = 0.0F;


  enum mtParams {
    alpha(0.0),
    beta(0.5),
    gamma(0.5);
    private double val;

    mtParams(double v) {
      this.val = v;
    }
  }

  private static Action<MomentumTrader> action(
      SerializableConsumer<MomentumTrader> consumer) {
    return Action.create(MomentumTrader.class, consumer);
  }

  @Override
  protected double getAlpha() {
    System.out.println("********* momentum trader strategy *********");
    System.out.println("Trader id: " + getID());

    System.out.println("alpha: " + mtParams.alpha.val);
    System.out.println("beta: " + mtParams.beta.val);
    System.out.println("gamma: " + mtParams.gamma.val);

    return mtParams.beta.val * getDemand();
  }

  private double getDemand() {
    return Math.tanh(Math.abs(momentum) * mtParams.gamma.val);
  }

  @Override
  protected Side getSide() {
    System.out.println("momentum: " + momentum);
    return momentum > 0 ? Side.BUY : Side.SELL;
  }

  @Override
  protected int getVolume() {
    int volume = (int) Math.abs(getPrng().normal(0, getGlobals().stdDev).sample() + momentum);
    System.out.println("Volume: " + volume);
    return volume;
  }

  public static Action<MomentumTrader> updateMomentum =
      action(
          t -> {
            float price = t.getMessageOfType(Messages.MarketPrice.class).getBody();

            if (t.lastMarketPrice != 0) {
              t.momentum =
                  mtParams.alpha.val * (price - t.lastMarketPrice) + (1.0 - mtParams.alpha.val)
                      * t.momentum;
              System.out.println("New momentum: " + t.momentum);
            }
            t.lastMarketPrice = price;
            System.out.println("last market price " + t.lastMarketPrice);
          }
      );


  public static Action<MomentumTrader> updateParamsAlpha =
      action(
          t -> {
            long tick = t.getMessageOfType(Messages.Tick.class).getBody();
            System.out.println("get tick " + tick);
            if (tick != 0) {
              mtParams.alpha.val = (float) (tick - 1) / tick;
            }
          }
      );

  /* share opinion to the social network */
  public static Action<MomentumTrader> shareOpinion =
      action(
          t -> {
            t.getLinks(SocialNetworkLink.class).send(Messages.TraderOpinionShared.class, t.opinion);
            System.out.println("Trader " + t.getID() + " sent opinion");
          });

  /* fetch the opinion from social network and update the self opinion accordingly */
  public static Action<MomentumTrader> fetchAndAdjustOpinion =
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

    int count = 0;
    for (double o : opinionsList) {
      if (Math.abs(o - opinion) < getGlobals().vicinityRange) {
        count++;
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
    System.out.println(count + " opinions out of " + opinionsList.length + " opinions considered");
  }

  /* take opinion from influencer */
  public void adjustOpinionWithInfluencerOpinion() {

    if (hasMessageOfType(InfluencerSocialNetworkOpinion.class)) {
      double influencerOpinion = getMessageOfType(InfluencerSocialNetworkOpinion.class).getBody();

      System.out.println("WOWWWWWWWW Opinion from Elon Musk: " + influencerOpinion);

//      if (opinion > 0) {
//        opinion += (influencerOpinion - opinion) * (getGlobals().gamma + 0.005);
//      }

      double confidenceFactor = (1 / (Math.abs(influencerOpinion - opinion) + getGlobals().k));
      opinion += (influencerOpinion - opinion) * confidenceFactor;
//      System.out.println("opinion after Elon: " + opinion);
    }
  }
}


