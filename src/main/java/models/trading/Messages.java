package models.trading;

import java.util.HashMap;
import java.util.List;
import simudyne.core.graph.Message;

public class Messages {

  public static class BuyOrderPlaced extends Message.Integer {}

  public static class SellOrderPlaced extends Message.Integer {}

  public static class ShortSellOrderPlaced extends Message.Integer {}

  public static class CloseShortPosOrderPlaced extends Message.Integer {}

  public static class MarketPrice extends Message.Float {}

  public static class MarketShock extends Message.Integer {} // for test purpose

  public static class TraderOpinionShared extends Message.Double {}

  public static class InfluencerOpinionShared extends Message.Double {}

  public static class SocialNetworkOpinion extends Message { List<java.lang.Double> opinionList; }

  public static class InfluencerSocialNetworkOpinion extends Message.Double {}

  public static class TrueValue extends Message.Double {}

  public static class Tick extends Message.Long {}

  public static class HistoricalPrices extends Message {
    HashMap<java.lang.Long, java.lang.Double> historicalPrices;
  }

  public static class NetDemand extends Message.Double {}
}
