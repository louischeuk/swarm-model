import models.trading.SwarmModel;
import simudyne.nexus.Server;

public class Main {
  public static void main(String[] args) {
    Server.register("Swarm", SwarmModel.class);
    Server.run();
  }
}
