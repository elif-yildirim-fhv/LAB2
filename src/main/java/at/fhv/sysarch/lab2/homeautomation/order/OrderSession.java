package at.fhv.sysarch.lab2.homeautomation.order;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.Fridge.*;
import at.fhv.sysarch.lab2.ordersystem.OrderProcessor;

import java.util.*;

public class OrderSession extends AbstractBehavior<OrderSession.OrderSessionCommand> {

    public interface OrderSessionCommand {}

    private static final class ProcessOrder implements OrderSessionCommand {}

    private static final class ReceiveReceipt implements OrderSessionCommand {
        final Receipt receipt;

        public ReceiveReceipt(Receipt receipt) {
            this.receipt = receipt;
        }
    }

    private final Product product;
    private final int amount;
    private final int maxProducts;
    private final double maxWeight;
    private final double currentWeight;
    private final Map<Product, Integer> products;
    private final ActorRef<OrderProcessor.OrderCommand> orderProcessor;
    private final List<Order> orderHistory;
    private final ActorRef<Fridge.OrderResponse> replyTo;

    public static Behavior<OrderSessionCommand> create(
            Product product, int amount, int maxProducts, double maxWeight,
            double currentWeight, Map<Product, Integer> products,
            ActorRef<OrderProcessor.OrderCommand> orderProcessor,
            List<Order> orderHistory, ActorRef<Fridge.OrderResponse> replyTo) {

        return Behaviors.setup(context ->
                new OrderSession(context, product, amount, maxProducts, maxWeight,
                        currentWeight, products, orderProcessor, orderHistory, replyTo));
    }

    private OrderSession(ActorContext<OrderSessionCommand> context,
                         Product product, int amount, int maxProducts, double maxWeight,
                         double currentWeight, Map<Product, Integer> products,
                         ActorRef<OrderProcessor.OrderCommand> orderProcessor,
                         List<Order> orderHistory, ActorRef<Fridge.OrderResponse> replyTo) {

        super(context);
        this.product = product;
        this.amount = amount;
        this.maxProducts = maxProducts;
        this.maxWeight = maxWeight;
        this.currentWeight = currentWeight;
        this.products = products;
        this.orderProcessor = orderProcessor;
        this.orderHistory = orderHistory;
        this.replyTo = replyTo;

        getContext().getSelf().tell(new ProcessOrder());
    }

    @Override
    public Receive<OrderSessionCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessOrder.class, this::onProcessOrder)
                .onMessage(ReceiveReceipt.class, this::onReceiveReceipt)
                .build();
    }

    private Behavior<OrderSessionCommand> onProcessOrder(ProcessOrder msg) {
        int currentTotal = products.values().stream().mapToInt(Integer::intValue).sum();
        if (currentTotal + amount > maxProducts) {
            replyTo.tell(new Fridge.OrderResponse(false,
                    "Order would exceed maximum product capacity", null));
            return Behaviors.stopped();
        }

        double newWeight = currentWeight + (product.weight() * amount);
        if (newWeight > maxWeight) {
            replyTo.tell(new Fridge.OrderResponse(false,
                    "Order would exceed maximum weight capacity", null));
            return Behaviors.stopped();
        }

        ActorRef<Receipt> adapter = getContext().messageAdapter(Receipt.class, ReceiveReceipt::new);
        orderProcessor.tell(new OrderProcessor.ProcessOrderCommand(product, amount, adapter));

        return this;
    }

    private Behavior<OrderSessionCommand> onReceiveReceipt(ReceiveReceipt msg) {

        Order order = new Order(product, amount, msg.receipt);
        orderHistory.add(order);

        products.merge(product, amount, Integer::sum);

        getContext().getLog().info("[DEVICE] Order processed: {} x {}, total price: €{}",
                amount, product.name(), msg.receipt.totalPrice());

        replyTo.tell(new Fridge.OrderResponse(true, "Order processed successfully", msg.receipt));

        return Behaviors.stopped();
    }
}