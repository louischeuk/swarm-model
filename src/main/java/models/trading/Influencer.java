package models.trading;

import models.trading.Links.SocialNetworkLink;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;


/* like Elon Musk */
public class Influencer extends Agent<TradingModel.Globals> {

  @Variable
  public int followers;

  @Variable
  public double opinion;

  private static Action<Influencer> action(SerializableConsumer<Influencer> consumer) {
    return Action.create(Influencer.class, consumer);
  }

  public static Action<Influencer> shareOpinion =
      action(
          i -> {
            i.getLinks(SocialNetworkLink.class).send(Messages.OpinionShared.class, i.opinion);
            System.out.println("Elon Musk (ID: " + i.getID() + ") sent opinion");
          });

}
