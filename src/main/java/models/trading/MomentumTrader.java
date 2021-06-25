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
  public double momentum = 0.0;

  float lastMarketPrice = 0.0F;

  private static Action<MomentumTrader> action(SerializableConsumer<MomentumTrader> consumer) {
    return Action.create(MomentumTrader.class, consumer);
  }

  @Override
  protected double getAlpha() {
    System.out.println("********* momentum trader strategy *********");
    System.out.println("Trader id: " + getID());
    return MtParams.beta * getDemand();
  }

  private double getDemand() {
    return Math.tanh(Math.abs(momentum + opinion) * MtParams.gamma);
  }

  @Override
  protected Side getSide() {
    System.out.println("momentum: " + momentum);
    return momentum > 0 ? Side.BUY : Side.SELL;
  }

  public static Action<MomentumTrader> updateMomentum =
      action(
          t -> {
            float price = t.getMessageOfType(Messages.MarketPrice.class).getBody();
            if (t.lastMarketPrice != 0) {
              t.momentum =
                  MtParams.alpha * (price - t.lastMarketPrice)
                      + (1.0 - MtParams.alpha) * t.momentum;
              System.out.println("New momentum: " + t.momentum);
            }
            t.lastMarketPrice = price;
            System.out.println("last market price " + t.lastMarketPrice);
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
//            t.adjustOpinionWithInfluencerOpinion();
            t.adjustOpinionWithTradersOpinions();
          });

  /* take opinion from other trader agents */
  public void adjustOpinionWithTradersOpinions() {

    List<Double> opinionsList = getMessageOfType(SocialNetworkOpinion.class).opinionList;
    getDoubleAccumulator("opinions").add(opinion);

    double count = opinionsList.stream().
        filter(o -> Math.abs(o - opinion) < getGlobals().vicinityRange).count();
    System.out.println(count + " opinions out of " + opinionsList.size() + " opinions considered");

    for (Double o : opinionsList) {
      if (Math.abs(o - opinion) < getGlobals().vicinityRange) {
        if (isSameSign(o) || // same direction
            (!isSameSign(o) && (Math.abs(o - momentum) < getGlobals().vicinityRange))) {
          opinion += (o - opinion) * getGlobals().gamma;
        }
      }
    }
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


