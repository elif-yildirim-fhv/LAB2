package at.fhv.sysarch.lab2.homeautomation.OrderSystem;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.Fridge.Product;
import at.fhv.sysarch.lab2.homeautomation.Fridge.Receipt;

import java.time.LocalDateTime;
import java.util.*;

public class OrderProcessor extends AbstractBehavior<OrderProcessor.OrderCommand> {

    // --- Nachrichten ---
    public interface OrderCommand {}

    public static final class ProcessOrderCommand implements OrderCommand {
        public final Product product;
        public final int amount;
        public final ActorRef<Receipt> replyTo;

        public ProcessOrderCommand(Product product, int amount, ActorRef<Receipt> replyTo) {
            this.product = product;
            this.amount = amount;
            this.replyTo = replyTo;
        }
    }

    // Factory-Methode
    public static Behavior<OrderCommand> create() {
        return Behaviors.setup(OrderProcessor::new);
    }

    private OrderProcessor(ActorContext<OrderCommand> context) {
        super(context);
        getContext().getLog().info("OrderProcessor started");
    }

    @Override
    public Receive<OrderCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessOrderCommand.class, this::onProcessOrder)
                .build();
    }

    private Behavior<OrderCommand> onProcessOrder(ProcessOrderCommand cmd) {
        // Bestellung verarbeiten (hier vereinfacht)
        String orderId = UUID.randomUUID().toString();
        double totalPrice = cmd.product.price() * cmd.amount;

        List<Product> items = new ArrayList<>();
        items.add(cmd.product);

        int[] quantities = new int[] { cmd.amount };

        // Quittung erstellen
        Receipt receipt = new Receipt(orderId, items, quantities, totalPrice, LocalDateTime.now());

        getContext().getLog().info("Processed order: {} x {}, total: €{}",
                cmd.amount, cmd.product.name(), totalPrice);

        // Quittung zurücksenden
        cmd.replyTo.tell(receipt);

        return this;
    }
}