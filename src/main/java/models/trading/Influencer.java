package models.trading;

import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.functions.SerializableConsumer;

/* eg. Elon Musk */
public class Influencer extends Agent<TradingModel.Globals> {

  public int followers;

  public double opinion; /* extremest: -1.0 / 1.0 */

  public double probabilityToShare;

  private static Action<Influencer> action(SerializableConsumer<Influencer> consumer) {
    return Action.create(Influencer.class, consumer);
  }

  public static Action<Influencer> shareOpinion =
      action(
          i -> {
            if (i.getPrng().uniform(0, 1).sample() < i.probabilityToShare) {
              i.getLinks(Links.SocialNetworkLink.class)
                  .send(Messages.InfluencerOpinionShared.class, i.opinion);
//              System.out.println("Elon Musk (ID: " + i.getID() + ") sent opinion");

            } else {
//              System.out.println("nope Elon Musk not posting");
            }
          });

}
