package models.trading;

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


  private void publishInfluencerOpinion() {
    // handle opinions from influencer agent
    if (hasMessagesOfType(Messages.InfluencerOpinionShared.class)) {
      double influencerOpinion =
          getMessageOfType(Messages.InfluencerOpinionShared.class).getBody();

      System.out.println(
          "Social media platform received Elon Musk's " + influencerOpinion + " opinion");

      getLinks(Links.SocialNetworkLink.class)
          .send(Messages.InfluencerSocialNetworkOpinion.class, influencerOpinion);
    }
  }

  private void publishTradersOpinions() {
    // handle opinions from trader agent
    double[] opinionsList = getMessagesOfType(Messages.OpinionShared.class).stream()
        .mapToDouble(Double::getBody).toArray();

    System.out.println(
        "Social media platform received " + opinionsList.length + " opinion");

    getLinks(Links.SocialNetworkLink.class)
        .send(Messages.SocialNetworkOpinion.class, (m, l) -> m.opinionList = opinionsList);

  }


}
