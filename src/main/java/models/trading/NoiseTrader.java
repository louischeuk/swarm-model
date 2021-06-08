package models.trading;

/* Noise trader (uninformed): Randomly buy or sell. Only contribute to noise */
public class NoiseTrader extends Trader {

  @Override
  protected void tradeStrategy() {

    System.out.println("Trader id: " + getID());
    System.out.println("^^^^^^^^^ noise trader strategy ^^^^^^^^^");

      // volume = N(0, sigma)
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
