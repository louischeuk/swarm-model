package models.trading;

import models.trading.TradingModel.Globals;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

/* eg. Bloomberg */
public class DataProvider extends Agent<Globals> {

  @Variable
  public double trueValue;

  @Variable
  public double accumulatedNetDemand = 0;

  /* --------- function definitions --------- */
  private static Action<DataProvider> action(SerializableConsumer<DataProvider> consumer) {
    return Action.create(DataProvider.class, consumer);
  }

  /* update true value V(t) - random walk: */
  public static Action<DataProvider> updateTrueValue =
      action(
          d -> {
            System.out.println("This is Bloomberg");

            double netDemand = d.getMessageOfType(Messages.NetDemand.class).getBody();
            d.accumulatedNetDemand += netDemand;

            /*
               sum of jump size = Y_i * N_t = N(0,s_j) * P(lambda)
               Note. s_j maybe == s_v.
               sd_j = 1, lambda = 2
            */
            double jumpDiffusionProcess =
                d.getPrng().normal(0, MarketParams.jumpDiffusionStdDev).sample()
                    * d.getPrng().poisson(MarketParams.jumpDiffusionPoissonLambda).sample();
            System.out.println("jumpDiffusionProcess: " + jumpDiffusionProcess);

            /*
               new True value = prev true value + random walk (dv_exo) + market signal (dv_endo)
               V(t) = V(t – 1) + N(0,sd_v) + jump diffusion process
                    = V(t – 1) + N(0,sd_v) + summation[i, N_t](Y_i)
            */
            System.out.println("prev True value: " + d.trueValue);

            double dv_exo =
                d.getPrng().normal(0, d.getGlobals().stdDev).sample() + jumpDiffusionProcess;

            double kyle_lambda = d.getGlobals().lambda * 2 / 3;
            double dv_endo = kyle_lambda * netDemand;
            System.out.println("Market signal: " + dv_endo);

//            m.trueValue = m.trueValue + dv_exo + dv_endo;
//            m.trueValue = m.trueValue < 0 ? 0 : m.trueValue;

            System.out.println("New True value: " + d.trueValue);
            d.getLinks(Links.DataProviderLink.class).send(Messages.TrueValue.class, d.trueValue);

//            System.out.println("New True value: " + trueValue);
          });
}
