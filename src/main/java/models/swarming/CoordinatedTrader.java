package models.swarming;

import simudyne.core.annotations.Variable;

/* group of traders that keep buying and strong holding */
public class CoordinatedTrader extends OpDynTrader {

  @Variable
  public double sigma_ct;

  @Override
  protected double getAlpha() {
    System.out.println("$$$$$$$$$$$ coordinated trader strategy $$$$$$$$$$$$$");
    return getGlobals().pCoordinatedTrade;
  }

  @Override
  protected double getVolume() { // change it to double

    if (getGlobals().tickCount == getGlobals().tickToStartOpDyn) {
      isOpDynOn = true;
      System.out.println("opinion starts to exchange");
    }

    return sigma_ct / getGlobals().numCoordinatedTrader;
  }

  @Override
  protected Side getSide() {
    return opinion > 0 ? Side.BUY : Side.SELL;
  }

  @Override
  protected void updateOpinion() {
    if (isOpDynOn) {
      double prevOpinion = opinion;
      adjustOpWithInfluencerOp();
      adjustOpWithDvTrueValue();
      sigma_ct = updateSigma(prevOpinion, sigma_ct);
    }
    getDoubleAccumulator("opinions").add(opinion);
  }

  @Override
  protected void adjustOpinionWithTradersOpinions() {}

}


  /*
    no one wants to buy, but large company has a very large of short sells,

    1. overvalue --> market-re-adjust (gradually) due to momentum trader and social network
    2. liquidity of the market --> more liquid <-> more change in market price
    3. limited wealth --> buy volume decreases

    4. run of the money (wealth only applies to the coordinates trader)

   */


