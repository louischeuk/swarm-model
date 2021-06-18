package models.trading;

/* Noise trader (uninformed): Randomly buy or sell. Only contribute to noise */
public class NoiseTrader extends Trader {

  @Override
  protected double getAlpha() {
    System.out.println("^^^^^^^^^ noise trader strategy ^^^^^^^^^");
    System.out.println("Trader id: " + getID());
    return getGlobals().probabilityNoiseTrade;
  }

  @Override
  protected Side getSide() { // 50% sell, 50% buy
    double p = getPrng().uniform(0, 1).sample();
    return p > 0.5 ? Side.BUY : Side.SELL;
  }

  @Override
  protected int getVolume() {
    int volume = (int) Math.abs(getPrng().normal(0, getGlobals().stdDev).sample());
    System.out.println("Volume: " + volume);
    return volume;
  }
}

