package models.trading;

import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public class Trader extends Agent<TradingModel.Globals> {

  @Variable
  public double tradingThresh;

//  @Variable
//  public double intrinsicValue;

//  @Variable
//  public double wealth;

//  @Variable
//  public double stock = 0;


  @Override
  public void init() {
    tradingThresh = getPrng().gaussian(0, 1).sample();
//    wealth = getPrng().exponential(1000).sample();
//    intrinsicValue = getPrng().normal(150, getGlobals().sigma).sample();
  }

  private static Action<Trader> action(SerializableConsumer<Trader> consumer) {
    return Action.create(Trader.class, consumer);
  }

  public static Action<Trader> processInformation() {
    return action(
        trader -> {
          double informationSignal = trader.getGlobals().informationSignal;

          if (Math.abs(informationSignal) > trader.tradingThresh) {
            if (informationSignal > 0) {
              trader.buy();
            } else {
              trader.sell();
            }
          }
        });
  }

  boolean shouldUpdateThreshold() {
    double updateFrequency = getGlobals().updateFrequency;
    return getPrng().uniform(0, 1).sample() <= updateFrequency;
  }

  public static Action<Trader> updateThreshold() {
    return action(
        trader -> {
          if (trader.shouldUpdateThreshold()) {
            trader.tradingThresh =
                trader.getMessageOfType(Messages.MarketPriceChange.class).getBody();
          }
        });
  }

  private void buy() {
    getLongAccumulator("buys").add(1);
    getLinks(Links.TradeLink.class).send(Messages.BuyOrderPlaced.class);
  }

  private void sell() {
    getLongAccumulator("sells").add(1);
    getLinks(Links.TradeLink.class).send(Messages.SellOrderPlaced.class);
  }

//  public static Action<Trader> createNewLink() {
//    Action.create(Trader.class, a -> {
//      a.getMessagesOfType(Messages.NewNeighborMessage.class).forEach(msg -> {
//        a.addLink(targetID, Links.NeighborTrader.class);
//      });
//    });
//  }
//}

}
