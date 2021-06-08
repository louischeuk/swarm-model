package models.trading;

import java.util.HashMap;
import simudyne.core.graph.Message;

public class Messages {

  public static class BuyOrderPlaced extends Message.Double {}

  public static class SellOrderPlaced extends Message.Double {}

  public static class MarketPrice extends Message.Double {}

  public static class ShortSellOrderPlaced extends Message.Double {}

  public static class CoverShortPosOrderPlaced extends Message.Double {}

  public static class MarketShock extends Message.Integer {}

  public static class TraderOpinionShared extends Message.Double {}

  public static class InfluencerOpinionShared extends Message.Double {}

  public static class SocialNetworkOpinion extends Message { double[] opinionList; }

  public static class InfluencerSocialNetworkOpinion extends Message.Double {}

  public static class TrueValue extends Message.Double {}

  public static class HistoricalPrices extends Message {
    HashMap<java.lang.Long, java.lang.Double> historicalPrices;
  }

  public static class Tick extends Message.Long {}
}
