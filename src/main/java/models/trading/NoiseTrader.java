package models.trading;

/* Noise trader (uninformed): will randomly buy or sell */
public class NoiseTrader extends Trader {

  @Override
  protected void tradeStrategy() {

    System.out.println("Trader id: " + getID());
    System.out.println("^^^^^^^^^ noise trader strategy ^^^^^^^^^");

    if (getPrng().uniform(0, 1).sample() < 0.5) {

      int volume = (int) getPrng().normal(0, 1 + Math.abs(opinion)).sample();

      System.out.println("Volume: " + volume);

      if (volume > 0) {
        handleWhenBuyShares(volume);
      } else {
        handleWhenSellShares(Math.abs(volume));
      }

    } else {
      hold();
    }
  }
}
