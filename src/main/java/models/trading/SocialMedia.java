package models.trading;

import models.trading.Messages.OpinionShared;
import models.trading.Messages.SocialMediaOpinion;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.graph.Message.Double;

public class SocialMedia extends Agent<TradingModel.Globals> {

  public static Action<SocialMedia> publishOpinions =
      Action.create(SocialMedia.class, s -> {

        double[] opinionsList = s.getMessagesOfType(OpinionShared.class).stream()
            .mapToDouble(Double::getBody).toArray();

        System.out.println("Social media platform received " + opinionsList.length + " opinion");

        s.getLinks(Links.SocialMediaLink.class)
            .send(SocialMediaOpinion.class, (m,l) -> m.opinionList = opinionsList);

          });


}
