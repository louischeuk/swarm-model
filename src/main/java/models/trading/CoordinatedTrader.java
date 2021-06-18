package models.trading;

import simudyne.core.annotations.Variable;

/* group of traders that keep buying and strong holding */
public class CoordinatedTrader extends Trader {

  @Variable
  public double opinion;

  @Override
  protected double getAlpha() {
    System.out.println("$$$$$$$$$$$ coordinated trader strategy $$$$$$$$$$$$$");
    System.out.println("Trader id: " + getID());
    return getGlobals().probabilityCoordinatedTrade;
  }

  @Override
  protected Side getSide() { return opinion > 0 ? Side.BUY : Side.SELL; }

  @Override
  protected int getVolume() {
    int volume = (int) Math.abs(getPrng().normal(0, getGlobals().stdDev).sample());
    System.out.println("Volume: " + volume);
    return volume;
  }

  // share opinion
  // change opinion?

}
