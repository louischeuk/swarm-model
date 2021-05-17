import models.trading.TradingModel;
import simudyne.nexus.Server;

public class Main {
  public static void main(String[] args) {
    Server.register("Swarm", TradingModel.class);
    Server.run();
  }


}
