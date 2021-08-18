package models.swarming;

import java.util.List;
import models.swarming.Links.SocialNetworkLink;
import models.swarming.Messages.InfluencerOpinionShared;
import models.swarming.Messages.SocialNetworkOpinion;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

/* eg. Elon Musk */
public class Influencer extends Agent<Globals> {

  @Variable
  public double opinion; /* extremest. be high to have a nice uptrend curve of market price */

  public boolean isHyped = false;

  public double hypedPoint = 10;
  private boolean isTweeted = false;

  private static Action<Influencer> action(SerializableConsumer<Influencer> consumer) {
    return Action.create(Influencer.class, consumer);
  }

  public static Action<Influencer> shareOpinion =
      action(
          i -> {
            if (i.isHyped && !i.isTweeted) {
              i.getLinks(SocialNetworkLink.class).send(InfluencerOpinionShared.class, i.opinion);
              System.out.println("Elon Musk (ID: " + i.getID() + ") sent opinion");
              i.isTweeted = true;
            }
          });

  public static Action<Influencer> updateOpinion =
      action(
          i -> {
            i.adjustOpWithDvTrueValue();
            i.adjustOpWithTradersOps();
            i.checkIfHyped();
          });

  private void checkIfHyped() {
    if (opinion >= hypedPoint && !isTweeted) {
      isHyped = true;
    }
  }

  private void adjustOpWithTradersOps() {
    if (hasMessageOfType(Messages.SocialNetworkOpinion.class)) {
      List<Double> opinionsList =
          getMessageOfType(SocialNetworkOpinion.class).opinionList;
      System.out.println("Elon get " + opinionsList.size() + " opinions");
      double avgOpList = opinionsList.stream().mapToDouble(o -> o).average().orElse(0.0);
      opinion += getGlobals().multiplier_i * (avgOpList - opinion);
    }
  }

  private void adjustOpWithDvTrueValue() {
    if (getGlobals().tickCount >= getGlobals().tickToStartOpDyn) {
      if (hasMessageOfType(Messages.DeltaTrueValue.class)) {
        System.out.println("Elon get the deltaTrueValue");
        double dv_trueValue = getMessageOfType(Messages.DeltaTrueValue.class).getBody();
        opinion += getGlobals().multiplier_i * dv_trueValue;
      }
    }
  }

}
