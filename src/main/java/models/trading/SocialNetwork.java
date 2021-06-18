package models.trading;

import com.google.common.primitives.Doubles;
import java.util.List;
import models.trading.Links.SocialNetworkLink;
import models.trading.Messages.TraderOpinionShared;
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
            s.publishInfluencerOpinion();
            s.publishTradersOpinions();
          });


  /* handle opinions from influencer agent */
  private void publishInfluencerOpinion() {
    if (hasMessagesOfType(Messages.InfluencerOpinionShared.class)) {
      double influencerOpinion = getMessageOfType(Messages.InfluencerOpinionShared.class).getBody();

      System.out.println("Social media platform received Elon's " + influencerOpinion + " opinion");

      getLinks(SocialNetworkLink.class)
          .send(Messages.InfluencerSocialNetworkOpinion.class, influencerOpinion);
    }
  }

  /* handle opinions from trader agent */
  private void publishTradersOpinions() {
    double[] opinionsListRaw = getMessagesOfType(TraderOpinionShared.class).stream()
        .mapToDouble(Double::getBody).toArray();

    List<java.lang.Double> opinionsList = Doubles.asList(opinionsListRaw);
    System.out.println("Social media platform received " + opinionsList.size() + " opinion");

    getLinks(SocialNetworkLink.class)
        .send(Messages.SocialNetworkOpinion.class, (s, t) -> s.opinionList = opinionsList);

  }


}
