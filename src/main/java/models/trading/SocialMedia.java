package models.trading;

import models.trading.Messages.OpinionShared;
import models.trading.Messages.SocialMediaOpinion;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.functions.SerializableConsumer;
import simudyne.core.graph.Message.Double;

public class SocialMedia extends Agent<TradingModel.Globals> {

  private static Action<SocialMedia> action(SerializableConsumer<SocialMedia> consumer) {
    return Action.create(SocialMedia.class, consumer);
  }

  public static Action<SocialMedia> publishOpinions =
      action(
          s -> {

            double[] opinionsList = s.getMessagesOfType(OpinionShared.class).stream()
                .mapToDouble(Double::getBody).toArray();

            System.out.println(
                "Social media platform received " + opinionsList.length + " opinion");

            s.getLinks(Links.SocialMediaLink.class)
                .send(SocialMediaOpinion.class, (m, l) -> m.opinionList = opinionsList);

          });


}
