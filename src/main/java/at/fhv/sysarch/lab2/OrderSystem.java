package at.fhv.sysarch.lab2;

import akka.actor.typed.ActorSystem;
import at.fhv.sysarch.lab2.ordersystem.OrderSystemHandler;

public class OrderSystem {

    public static void main(String[] args) {
        ActorSystem<Void> system = ActorSystem.create(OrderSystemHandler.create(), "OrderSystem");
    }
}
