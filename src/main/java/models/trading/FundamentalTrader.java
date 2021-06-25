package models.trading;

import models.trading.Messages.MarketPrice;
import simudyne.core.abm.Action;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

/* Fundamental(informed) trader: force to buy and sell at prices bounded by the intrinsic value */
public class FundamentalTrader extends Trader {

  @Variable
  public double intrinsicValue;
  private double priceDistortion;
  double zScore;

  /* ------------------ functions definition -------------------------*/

  private static Action<FundamentalTrader> action(
      SerializableConsumer<FundamentalTrader> consumer) {
    return Action.create(FundamentalTrader.class, consumer);
  }

  @Override
  protected double getAlpha() {
    System.out.println("-------------- fundamental trader strategy --------------");
    System.out.println("Trader id: " + getID());

    return Math.abs(getPriceDistortion()) * getGlobals().sensitivity;
  }

  @Override
  protected Side getSide() {
    return priceDistortion > 0 ? Side.BUY : Side.SELL;
  }

  private double getPriceDistortion() {
    float price = getMessageOfType(MarketPrice.class).getBody();
    System.out.println("Intrinsic: " + intrinsicValue);
    System.out.println("Market price: " + price);

    priceDistortion = intrinsicValue - price;
    return priceDistortion;
  }

  /* random walk - brownian motion - keep estimate */
  public static Action<FundamentalTrader> adjustIntrinsicValue =
      action(
          t -> {

            System.out.println("adjust intrinsic value");
            if (t.hasMessageOfType(Messages.MarketShock.class)) {
              System.out.println("Market shock is triggered!!!!!!!!!!!!!!");
            }

            double trueValue = t.getMessageOfType(Messages.TrueValue.class).getBody();
//            System.out.println("Trader " + t.getID() + " prev intrinsic value: " + t.intrinsicValue);
            t.intrinsicValue = t.zScore * t.getGlobals().stdDev + trueValue;
            t.intrinsicValue = t.intrinsicValue <= 0 ? 0 : t.intrinsicValue;
//            System.out.println("Trader " + t.getID() + " new intrinsic value: " + t.intrinsicValue);
          });


}

