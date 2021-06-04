package models.trading;

  /*
    1. look at if it is true momentum
      look at volume
      look at how quickly it is moved
      look at distance: swing trading -> how far is it moved in a week/ a month
      is it a reason why people comes in
      great data? bad data?

      burst of move that drive the push. you can see there's a lot of volume behind it
      there's a lot of institutional buy, a lot of participant involve


    2. find the entry point
      if jump early --> not gonna be sure if it is true momentum --> risk brough high
      if wait to confirm --> wait for the pullback, wait for the good entry

      momentum in bear/ bull market works very well



      There's no specific dividing line between the two.
      Howerver, high volume stocks typically trade at a volume of 500,000 or more shares per day.
      Low volume stocks would be below that mark.


      Divide today’s close by the close a certain number of days ago.

      For example, you can look back five days.

      Multiply that number by 100.

      M = (Price Today/Price Five Days Ago) x100

      M = (15/10) x 100 = 150
   */

public class MomentumTrader extends Trader {

  @Override
  protected void tradeStrategy() {

  }
}
