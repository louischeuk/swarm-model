package models.swarming;

import models.swarming.Links.SocialNetworkLink;
import models.swarming.Messages.InfluencerSocialNetworkOpinion;
import simudyne.core.abm.Action;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public abstract class OpDynTrader extends Trader {

  @Variable
  public double opinion;

  boolean isOpDynOn = false;

  private static Action<OpDynTrader> action(SerializableConsumer<OpDynTrader> consumer) {
    return Action.create(OpDynTrader.class, consumer);
  }

  /* ------ from Trader class -------------- */
  protected abstract double getAlpha();

  protected abstract Side getSide();

  protected abstract double getVolume();

  /* ------- from OpDynTrader class ------------- */

  protected abstract void updateOpinion();

  protected abstract void adjustOpinionWithTradersOpinions();

  /* share opinion to the social network */
  public static Action<OpDynTrader> shareOpinion =
      action(
          t -> {
            t.getLinks(SocialNetworkLink.class).send(Messages.TraderOpinionShared.class, t.opinion);
            System.out.println("Trader " + t.getID() + " sent opinion");
          });

  /* fetch the opinion from social network and update the self opinion accordingly */
  public static Action<OpDynTrader> updateOpinion =
      action(OpDynTrader::updateOpinion);

  /* take opinion from influencer */
  protected void adjustOpWithInfluencerOp() {
    if (hasMessageOfType(InfluencerSocialNetworkOpinion.class)) {
      double influencerOpinion = getMessageOfType(InfluencerSocialNetworkOpinion.class).getBody();
      System.out.println("Elon post a tweet !!!!!!!!!!!!!!!!! " + influencerOpinion);
      opinion += influencerOpinion * getGlobals().influencer_k; // take influencer opinion

      // update using fixed factor / confidence factor?

      // double confidenceFactor = (1 / (Math.abs(influencerOpinion - opinion) + getGlobals().k));
      // opinion += (influencerOpinion - opinion) * confidenceFactor;
    }
  }

  protected double updateSigma(double prevOpinion, double sigma) {

    double prevOpinionCopy = Math.abs(prevOpinion);
    double opinionCopy = Math.abs(opinion);

    if (prevOpinionCopy < opinionCopy) {
      sigma = (opinionCopy * sigma / prevOpinionCopy) * getGlobals().multiplier;
    } else {
      sigma = (opinionCopy * sigma / prevOpinionCopy) / getGlobals().multiplier;
    }
    return sigma;

//    if (prevOpinion < 0 && opinion < 0) {
//      if (prevOpinion < opinion) {
//        sigma = (opinion * sigma / prevOpinion) / getGlobals().multiplier;
//      } else {
//        sigma = (opinion * sigma / prevOpinion) * getGlobals().multiplier;
//      }
//    }
//    else if (prevOpinion > 0 && opinion > 0) {
//      if (prevOpinion < opinion) {
//        sigma = (opinion * sigma / prevOpinion) * getGlobals().multiplier;
//      } else {
//        sigma = (opinion * sigma / prevOpinion) / getGlobals().multiplier;
//      }
//    }
//    else if (prevOpinion < 0 && opinion > 0) {
//      sigma = -1 * (opinion * sigma / prevOpinion) * getGlobals().multiplier;
//    }
//    else if (prevOpinion > 0 && opinion < 0) {
//      sigma = -1 * (opinion * sigma / prevOpinion) / getGlobals().multiplier;
//    }
//    return sigma <= 0 ? 0 : sigma;

//    if (prevOpinion > 0) {
//      if (prevOpinion < opinion) {
//        sigma = (opinion * sigma / prevOpinion) * getGlobals().multiplier;
//      } else {
//        sigma = (opinion * sigma / prevOpinion) / getGlobals().multiplier;
//      }
//      return sigma <= 0 ? 0 : sigma;
//    } else {
//      return getGlobals().sigma_ct;
//    }
  }

  protected void adjustOpWithDvTrueValue() {
    System.out.println("Inside adjustWithDeltaTrueValue");
    if (hasMessageOfType(Messages.DeltaTrueValue.class)) {

      double dv_trueValue = getMessageOfType(Messages.DeltaTrueValue.class).getBody();

      System.out.println(type + " trader received delta of true value: " + dv_trueValue);
      double dv_opinion = getGlobals().weightingForDeltaOpinion * dv_trueValue;
      opinion += dv_opinion;
    }
  }

}
