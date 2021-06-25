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
//    int volume = (int) Math.abs(getPrng().normal(0, getGlobals().stdDev).sample());
//    System.out.println("Volume: " + volume);
//    return volume;
    return 1;
  }

  // share opinion
  // change opinion?


  /*
    no one wants to buy, but large company has a very large of short sells,

    1. overvalue --> market-re-adjust (gradually) due to momentum trader and social network
    2. liquidity of the market --> more liquid <-> more change in market price
    3.


   */

}
