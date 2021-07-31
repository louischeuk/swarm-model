package models.trading;

import simudyne.core.abm.Action;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

/* Fundamental(informed) trader: force to buy and sell at prices bounded by the intrinsic value */
public class FundamentalTrader extends Trader {

  @Variable
  public double intrinsicValue;

  private double priceDistortion;

  @Variable
  public double zScore;

  /* ------------------ functions definition -------------------------*/

  private static Action<FundamentalTrader> action(
      SerializableConsumer<FundamentalTrader> consumer) {
    return Action.create(FundamentalTrader.class, consumer);
  }

  @Override
  protected double getAlpha() {
    System.out.println("-------------- fundamental trader strategy --------------");
    return 1;
  }

  @Override
  protected double getVolume() {
    double ftParam_kappa = getGlobals().ftParam_kappa;
    int numFundamentalTrader = getGlobals().numFundamentalTrader;
    return (ftParam_kappa / numFundamentalTrader) * Math.abs(getPriceDistortion());
  }

  @Override
  protected Side getSide() {
    return priceDistortion > 0 ? Side.BUY : Side.SELL;
  }

  private double getPriceDistortion() {
    System.out.println("Intrinsic: " + intrinsicValue);
    priceDistortion = intrinsicValue - getMarketPrice();
    return priceDistortion;
  }

  /* random walk - brownian motion - keep estimate */
  public static Action<FundamentalTrader> adjustIntrinsicValue =
      action(
          t -> {
            if (t.hasMessageOfType(Messages.MarketShock.class)) {
              System.out.println("Market shock is triggered!!!!!!!!!!!!!!");
            }

            double trueValue = t.getMessageOfType(Messages.TrueValue.class).getBody();
            t.intrinsicValue = t.zScore * t.getGlobals().sigma_u + trueValue;
            t.intrinsicValue = t.intrinsicValue <= 0 ? 0 : t.intrinsicValue;
          });
}


// how to push the price of FT intrinsic
// 1. FT takes opinions in account?
// 2. sigma_u != 0

