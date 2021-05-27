package models.trading;

/* Fundamental trader (informed):
   force to buy and sell at prices bounded by the intrinsic value */
public class FundamentalTrader extends Trader {

  @Override
  protected void tradeStrategy() {

    System.out.println("Trader id: " + getID());
    System.out.println("-------------- fundamental trader strategy --------------");

    double price = getGlobals().marketPrice;
    double priceDistortion = intrinsicValue - price;

    System.out.println("Intrinsic: " + intrinsicValue);
    System.out.println("market price: " + getGlobals().marketPrice);

    double alpha = priceDistortion * getGlobals().sensitivity;

    // if U(0,1) < alpha: buy / sell else hold
    if (getPrng().uniform(0, 1).sample() < Math.abs(alpha)
        && alpha != 0) {

      int volume = (int) Math.ceil(Math.abs(alpha));

      if (alpha > 0) {        // buy
        System.out.println("Amount shares to buy: " + volume);
        handleWhenBuyShares(volume);

      } else if (alpha < 0) { // sell
        System.out.println("Amount shares to sell: " + volume);
        handleWhenSellShares(volume);
      }

    } else {
      hold();
    }
  }

}
