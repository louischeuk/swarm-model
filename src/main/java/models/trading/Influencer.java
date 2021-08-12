package models.trading;

import models.trading.Links.SocialNetworkLink;
import models.trading.Messages.InfluencerOpinionShared;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.functions.SerializableConsumer;

/* eg. Elon Musk */
public class Influencer extends Agent<Globals> {

  double opinion; /* extremest. be high to have a nice uptrend curve of market price */

  private static Action<Influencer> action(SerializableConsumer<Influencer> consumer) {
    return Action.create(Influencer.class, consumer);
  }

  public static Action<Influencer> shareOpinion =
      action(
          i -> {
            double p = i.getPrng().uniform(0, 1).sample();
            if (p < (i).getGlobals().pInfluencerShare) {
              i.getLinks(SocialNetworkLink.class).send(InfluencerOpinionShared.class, i.opinion);
              System.out.println("Elon Musk (ID: " + i.getID() + ") sent opinion");
            } else {
              System.out.println("nope, influencer not posting");
            }
          });

}
