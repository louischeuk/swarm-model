package models.trading;


public class NoiseTrader extends Trader {

  @Override
  protected void tradeStrategy() {
    System.out.println("noise trader strategy - no implementation yet");
    hold();
  }
}
