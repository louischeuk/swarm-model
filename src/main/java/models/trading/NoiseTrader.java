package models.trading;

/*
  Noise trader (uninformed): - will randomly buy or sell
                                - only contribute to nose

  They cause the market to artificially react to their trades and can
  send prices and stock movements surging in one direction or another,
  even if all other traders act in a rational way.

  Noise traders are usually linked to high volume trading days.
  They typically end up overinflating securities during bullish periods
  and excessively deflating them during bearish periods.
  In most cases, noise traders are investors without a professional background in trading.
*/

public class NoiseTrader extends Trader {

  @Override
  protected void tradeStrategy() {

    System.out.println("Trader id: " + getID());
    System.out.println("^^^^^^^^^ noise trader strategy ^^^^^^^^^");

      // volume = N(0, 10)
      int volume = (int) getPrng().normal(0, getGlobals().stdDev).sample();
      System.out.println("Volume: " + volume);

      if (getPrng().uniform(0,1).sample() < getGlobals().noiseTraderActivity) {
        if (volume > 0) {
          handleWhenBuyShares(volume);
        } else {
          handleWhenSellShares(Math.abs(volume));
        }
      }
  }

}
