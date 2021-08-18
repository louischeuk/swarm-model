package models.swarming;

import com.google.common.primitives.Doubles;
import java.util.List;
import models.swarming.Links.SocialNetworkLink;
import models.swarming.Messages.TraderOpinionShared;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.functions.SerializableConsumer;
import simudyne.core.graph.Message.Double;

public class SocialNetwork extends Agent<Globals> {

  private static Action<SocialNetwork> action(SerializableConsumer<SocialNetwork> consumer) {
    return Action.create(SocialNetwork.class, consumer);
  }

  public static Action<SocialNetwork> publishOpinionsAndDeltaTrueValue =
      action(
          s -> {
            s.publishTrueValueToTraders();
            s.publishInfluencerOpinion(); // ????????????
            s.publishTradersOpinions();
          });

  private void publishTrueValueToTraders() {
    if (hasMessageOfType(Messages.DeltaTrueValue.class)) {
      double dv_trueValue = getMessageOfType(Messages.DeltaTrueValue.class).getBody();
      System.out.println("Received change in true value from data provider: " + dv_trueValue);
      getLinks(SocialNetworkLink.class).send(Messages.DeltaTrueValue.class, dv_trueValue);
    }
  }

  /* handle opinions from trader agents */
  private void publishTradersOpinions() {
    double[] opinionsListRaw = getMessagesOfType(TraderOpinionShared.class).stream()
        .mapToDouble(Double::getBody).toArray();

    List<java.lang.Double> opinionsList = Doubles.asList(opinionsListRaw);
    System.out.println("Social media platform received " + opinionsList.size() + " opinion");

    getLinks(SocialNetworkLink.class)
        .send(Messages.SocialNetworkOpinion.class, (s, t) -> s.opinionList = opinionsList);

  }

  /* handle opinions from influencer agent */
  private void publishInfluencerOpinion() {
    if (hasMessagesOfType(Messages.InfluencerOpinionShared.class)) {
      double influencerOpinion = getMessageOfType(Messages.InfluencerOpinionShared.class).getBody();

      System.out.println("Social media platform received Elon's " + influencerOpinion + " opinion");

      getLinks(SocialNetworkLink.class)
          .send(Messages.InfluencerSocialNetworkOpinion.class, influencerOpinion);
    }
  }




}
