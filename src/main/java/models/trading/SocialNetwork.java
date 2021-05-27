package models.trading;

import models.trading.Links.SocialNetworkLink;
import models.trading.Messages.OpinionShared;
import models.trading.Messages.SocialMediaOpinion;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.functions.SerializableConsumer;
import simudyne.core.graph.Message.Double;

public class SocialNetwork extends Agent<TradingModel.Globals> {

  private static Action<SocialNetwork> action(SerializableConsumer<SocialNetwork> consumer) {
    return Action.create(SocialNetwork.class, consumer);
  }

  public static Action<SocialNetwork> publishOpinions =
      action(
          s -> {

            double[] opinionsList = s.getMessagesOfType(OpinionShared.class).stream()
                .mapToDouble(Double::getBody).toArray();

            System.out.println(
                "Social media platform received " + opinionsList.length + " opinion");

            s.getLinks(SocialNetworkLink.class)
                .send(SocialMediaOpinion.class, (m, l) -> m.opinionList = opinionsList);

          });


}
