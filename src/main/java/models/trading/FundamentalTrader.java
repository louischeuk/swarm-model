package models.trading;

import models.trading.Links.SocialNetworkLink;
import models.trading.Messages.InfluencerSocialNetworkOpinion;
import models.trading.Messages.MarketPrice;
import simudyne.core.abm.Action;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

/* Fundamental(informed) trader: force to buy and sell at prices bounded by the intrinsic value */
public class FundamentalTrader extends Trader {

  @Variable
  public double intrinsicValue;

  @Variable
  public double intrinsicNoOpnDynamics;

  @Variable
  public double opinion; /*
                           opinion = zScore
                                   = N(0, 1) for v_i(t) = opinion * sd + V(t)
                           intention: to fixed the direction of intrinsic
                           will adjust with other opinions
                        */

  @Variable
  public double zScore; /* intrinsic Value without opinion for comparison */

  double priceDistortion;

  /* ------------------ functions definition -------------------------*/

  private static Action<FundamentalTrader> action(
      SerializableConsumer<FundamentalTrader> consumer) {
    return Action.create(FundamentalTrader.class, consumer);
  }

  @Override
  protected double getAlpha() {

    System.out.println("-------------- fundamental trader strategy --------------");
    System.out.println("Trader id: " + getID());

    priceDistortion = getPriceDistortion();
    return Math.abs(priceDistortion) * getGlobals().sensitivity;
  }

  @Override
  protected int getVolume() {
    return (int) Math.ceil(Math.abs(priceDistortion) * getGlobals().sensitivity);
  }

  @Override
  protected Side getSide() {
    return priceDistortion > 0 ? Side.BUY : Side.SELL;
  }

  private double getPriceDistortion() {
    float price = getMessageOfType(MarketPrice.class).getBody();
    System.out.println("Intrinsic: " + intrinsicValue);
    System.out.println("market price: " + price);

    return intrinsicValue - price;
  }

//  @Override
//  protected void tradeStrategy() {
//
//    System.out.println("-------------- fundamental trader strategy --------------");
//    System.out.println("Trader id: " + getID());
//
//    double price = getMessageOfType(MarketPrice.class).getBody();
//    System.out.println("get a market price" + price);
//
//    double priceDistortion = intrinsicValue - price;
//
//    System.out.println("Intrinsic: " + intrinsicValue);
//    System.out.println("market price: " + price);
//
//    double alpha = Math.abs(priceDistortion) * getGlobals().sensitivity;
//
//    // if U(0,1) < alpha: buy / sell else hold
//    if ((getPrng().uniform(0, 1).sample() < Math.abs(alpha)) && alpha != 0) {
//
//      int volume = (int) Math.ceil(Math.abs(alpha));
//
//      if (alpha > 0) {        // buy
//        System.out.println("Amount shares to buy: " + volume);
//        handleWhenBuyShares(volume);
//
//      } else if (alpha < 0) { // sell
//        System.out.println("Amount shares to sell: " + volume);
//        handleWhenSellShares(volume);
//      }
//
//    } else {
//      hold();
//    }
//  }

  /* random walk - brownian motion - keep estimate */
  public static Action<FundamentalTrader> adjustIntrinsicValue =
      action(
          t -> {

//            if (t.hasMessageOfType(Messages.MarketShock.class)) {
//              System.out.println("Market shock is triggered!!!!!!!!!!!!!!");
//            }

            double trueValue = t.getMessageOfType(Messages.TrueValue.class).getBody();

//            System.out.println("Trader " + t.getID() + " prev intrinsic value: " + t.intrinsicValue);

            /*
               control set-up: absent of social network
               --- intrinsic value (v_i(t) for trader i, step t) without opinion ---
               v_i(t) = opinion * sd  + V(t)
               v_i(t) ~ N(V(t), sd) if opinion ~ N(0,1)
            */
            t.intrinsicNoOpnDynamics = t.zScore * t.getGlobals().stdDev + trueValue;
            t.intrinsicNoOpnDynamics = t.intrinsicNoOpnDynamics <= 0 ? 0 : t.intrinsicNoOpnDynamics;

            /* update intrinsic with social network */
            t.intrinsicValue = (t.opinion * t.getGlobals().stdDev) + trueValue;
            t.intrinsicValue = t.intrinsicValue <= 0 ? 0 : t.intrinsicValue;

//            System.out.println("Trader " + t.getID() + " new intrinsic value: " + t.intrinsicValue);
          });





}

