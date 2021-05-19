package models.trading;

import simudyne.core.graph.Message;

public class Messages {
  public static class BuyOrderPlaced extends Message.Double {}

  public static class SellOrderPlaced extends Message.Double {}

  public static class MarketPrice extends Message.Double {}

}
