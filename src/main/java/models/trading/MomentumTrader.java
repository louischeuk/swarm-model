package models.trading;

import java.util.List;
import models.trading.Links.SocialNetworkLink;
import models.trading.Messages.InfluencerSocialNetworkOpinion;
import models.trading.Messages.SocialNetworkOpinion;
import simudyne.core.abm.Action;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public class MomentumTrader extends Trader {

  @Variable
  public double opinion;

  @Variable
  public double momentum = 0.0; // 0.075 for testing

  float lastMarketPrice = 0.0F; //  5.0f for testing

  boolean isOpinionOn = false;

  int countTick = 0;

  private static Action<MomentumTrader> action(SerializableConsumer<MomentumTrader> consumer) {
    return Action.create(MomentumTrader.class, consumer);
  }

  @Override
  protected double getAlpha() {
    System.out.println("********* momentum trader strategy *********");
    return 1;
  }

  private double getDemand() {

    if (++countTick == getGlobals().tickToStartOpinionExchange) {
      isOpinionOn = true;
      System.out.println("opinion starts to exchange");
    }

    double demand;
    if (isOpinionOn) { // isOpinion = true
      demand = Math.tanh(Math.abs(momentum + opinion) * getGlobals().mtParams_gamma);
    } else { // isOpinion = false
      demand = Math.tanh(Math.abs(momentum) * getGlobals().mtParams_gamma);
    }
    getDoubleAccumulator("MtDemand").add(demand);
    return demand;
  }

  @Override
  protected double getVolume() {
    return (getGlobals().mtParams_beta / getGlobals().numMomentumTrader) * getDemand();
  }

  @Override
  protected Side getSide() {
    System.out.println("momentum: " + momentum);
    return (momentum + opinion) > 0 ? Side.BUY : Side.SELL;
  }


  public static Action<MomentumTrader> updateMomentum =
      action(
          t -> {
            float price = t.getMarketPrice();
            double mtParams_alpha = t.getGlobals().mtParams_alpha;
            if (t.lastMarketPrice != 0) {
              t.momentum = mtParams_alpha * (price - t.lastMarketPrice)
                      + (1.0 - mtParams_alpha) * t.momentum;
            }
            t.lastMarketPrice = price;
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

            if (t.isOpinionOn) {
              //            t.adjustOpinionWithInfluencerOpinion();
              t.adjustOpinionWithTradersOpinions();
            }
            t.getDoubleAccumulator("opinions").add(t.opinion);

          });

  /* take opinion from other trader agents */
  public void adjustOpinionWithTradersOpinions() {

    List<Double> opinionsList = getMessageOfType(SocialNetworkOpinion.class).opinionList;

    int count = 0;
    for (Double o : opinionsList) {
      if (Math.abs(o - opinion) < getGlobals().vicinityRange) {
        if (isSameSign(o) || (Math.abs(o - momentum) < (getGlobals().vicinityRange + 0.5))) {
          opinion += (o - opinion) * getGlobals().gamma;
          count++;
        }
      }
    }
    System.out.println(count + " opinions out of " + opinionsList.size() + " opinions considered");
  }

  /* dynamics confidence factor */
  // it doesnt work well because the opinions considered are still close to the self opinion,
  // so it converges super quickly
//        double gamma = 1 / (Math.abs(o - opinion) + 1);
//        double beta = 1 - gamma;
//        /* opinion = opinion * selfConfidence + otherOpinion * ConfidenceToOther */
//        opinion = opinion * beta + o * gamma;

  private boolean isSameSign(Double o) {
    return (o > 0 && momentum > 0) || (o < 0 && momentum < 0);
  }

  /*
    take account of momentum ****!!!!!!!!!!!!!!!
    opinion + and market goes up --> belief
    opinion + but market goes own --> tend not to belief

    eg.
    a) momentum: 5, other opinion: 2 --> must belief
    b) momentum: 1, other opinion: 2 --> still belief
    c) momentum: -1, other opinion: 2 --> not belief
  */


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


