package models.trading;

import models.trading.Links.SocialNetworkLink;
import simudyne.core.abm.Action;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

/* group of traders that keep buying and strong holding */
public class CoordinatedTrader extends Trader {

  @Variable
  public double opinion;

  private static Action<CoordinatedTrader> action(SerializableConsumer<CoordinatedTrader> consumer) {
    return Action.create(CoordinatedTrader.class, consumer);
  }

  @Override
  protected double getAlpha() {
    System.out.println("$$$$$$$$$$$ coordinated trader strategy $$$$$$$$$$$$$");
    System.out.println("Trader id: " + getID());
    return getGlobals().pCoordinatedTrade;
  }

  @Override
  protected double getVolume() { // change it to double
    return getGlobals().sigma_ct / getGlobals().numCoordinatedTrader;
  }

  @Override
  protected Side getSide() { return opinion > 0 ? Side.BUY : Side.SELL; }


  /* share opinion to the social network */
  public static Action<CoordinatedTrader> shareOpinion =
      action(
          t -> {
            t.getLinks(SocialNetworkLink.class).send(Messages.TraderOpinionShared.class, t.opinion);
            System.out.println("Trader " + t.getID() + " sent opinion");
          });


  /*
    no one wants to buy, but large company has a very large of short sells,

    1. overvalue --> market-re-adjust (gradually) due to momentum trader and social network
    2. liquidity of the market --> more liquid <-> more change in market price
    3. limited wealth --> buy volume decreases

    4. run of the money (wealth only applies to the coordinates trader)


   */

}
