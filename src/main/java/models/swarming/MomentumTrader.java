package models.swarming;

import java.util.List;
import models.swarming.Messages.SocialNetworkOpinion;
import simudyne.core.abm.Action;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public class MomentumTrader extends OpDynTrader {

  @Variable
  public double momentum = 0.0; // 0.075 for testing

  float lastMarketPrice = 0.0F; //  5.0f for testing

  @Variable
  public double mtParams_beta;

  private static Action<MomentumTrader> action(SerializableConsumer<MomentumTrader> consumer) {
    return Action.create(MomentumTrader.class, consumer);
  }

  @Override
  protected double getAlpha() {
    System.out.println("********* momentum trader strategy *********");
    return getGlobals().pMomentumTrade;
  }

  private double getDemand() {

    if (getGlobals().tickCount == getGlobals().tickToStartOpDyn) {
      isOpDynOn = true;
      System.out.println("opinion starts to exchange");
    }

    double demand;
    if (isOpDynOn) {
      demand = Math.tanh(Math.abs(momentum + opinion) * getGlobals().mtParams_gamma);
    } else {
      demand = Math.tanh(Math.abs(momentum) * getGlobals().mtParams_gamma);
    }
    getDoubleAccumulator("MtDemand").add(demand);
    return demand;
  }

  @Override
  protected double getVolume() {
    return (mtParams_beta / getGlobals().numMomentumTrader) * getDemand(); // changed
  }

  @Override
  protected Side getSide() {
    System.out.println("momentum: " + momentum);
    return (momentum + opinion) > 0 ? Side.BUY : Side.SELL;
  }

  public static Action<MomentumTrader> updateMomentum =
      action(
          t -> {
            float price = t.getMarketPrice();
            double mtParams_alpha = t.getGlobals().mtParams_alpha;
            if (t.lastMarketPrice != 0) {
              t.momentum = mtParams_alpha * (price - t.lastMarketPrice)
                  + (1.0 - mtParams_alpha) * t.momentum;
            }
            t.lastMarketPrice = price;
            System.out.println("momentum updated");
          }
      );

  @Override
  protected void updateOpinion() {
    System.out.println("Trader ID " + getID() + " received opinion");

    if (isOpDynOn) {
      double prevOpinion = opinion;
      adjustOpWithInfluencerOp();
      adjustOpinionWithTradersOpinions();
//      adjustOpinionWithDvTrueValue();

      mtParams_beta = updateSigma(prevOpinion, mtParams_beta);
    }
    getDoubleAccumulator("opinions").add(opinion);

  }

  /* take opinion from other trader agents */
  public void adjustOpinionWithTradersOpinions() {
    List<Double> opinionsList = getMessageOfType(SocialNetworkOpinion.class).opinionList;

    int count = 0;
    for (Double o : opinionsList) {
      if (Math.abs(o - opinion) < getGlobals().vicinityRange) {
//        opinion += (o - opinion) * getGlobals().gamma;

        double confidenceFactor = 1 / ((Math.abs(o - opinion) + getGlobals().cf_weighting));
        opinion += (o - opinion) * confidenceFactor;

        count++;
      }
    }
    System.out.println(count + " opinions out of " + opinionsList.size() + " opinions considered");
  }

  /* dynamics confidence factor */
  // it doesnt work well because the opinions considered are still close to the self opinion,
  // so it converges super quickly
//        double gamma = 1 / (Math.abs(o - opinion) + 1);
//        double beta = 1 - gamma;)
//        /* opinion = opinion * selfConfidence + otherOpinion * ConfidenceToOther */
//        opinion = opinion * beta + o * gamma;



//  private boolean isSameSign(Double o) {
//    return (o > 0 && momentum > 0) || (o < 0 && momentum < 0);
//  }



  /*
    take account of momentum ****!!!!!!!!!!!!!!!
    opinion + and market goes up --> belief
    opinion + but market goes own --> tend not to belief

    eg.
    a) momentum: 5, other opinion: 2 --> must belief
    b) momentum: 1, other opinion: 2 --> still belief
    c) momentum: -1, other opinion: 2 --> not belief
  */




}


