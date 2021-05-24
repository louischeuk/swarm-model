package models.trading;

import simudyne.core.graph.Message;

public class Messages {
  public static class BuyOrderPlaced extends Message.Double {}

  public static class SellOrderPlaced extends Message.Double {}

  public static class MarketPrice extends Message.Double {}

  public static class ShortSellOrderPlaced extends Message.Double {}
  
  public static class CoverShortPosOrderPlaced extends Message.Double {}
  
  public static class Opinion extends Message.Double {}

  public static class MarketShock extends Message.Integer {}
}
