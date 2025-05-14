package at.fhv.sysarch.lab2.homeautomation.Fridge;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.order.Order;
import at.fhv.sysarch.lab2.ordersystem.OrderProcessor;
import at.fhv.sysarch.lab2.homeautomation.order.OrderSession;

import java.util.*;



public class Fridge extends AbstractBehavior<Fridge.FridgeCommand> {

    public interface FridgeCommand {}

    public static final class AddProductCommand implements FridgeCommand {
        public final Product product;
        public final int amount;
        public final ActorRef<OperationResult> replyTo;

        public AddProductCommand(Product product, int amount, ActorRef<OperationResult> replyTo) {
            this.product = product;
            this.amount = amount;
            this.replyTo = replyTo;
        }
    }

    public static final class ConsumeProductCommand implements FridgeCommand {
        public final String productName;
        public final int amount;
        public final ActorRef<OperationResult> replyTo;

        public ConsumeProductCommand(String productName, int amount, ActorRef<OperationResult> replyTo) {
            this.productName = productName;
            this.amount = amount;
            this.replyTo = replyTo;
        }
    }

    public static final class GetProductsCommand implements FridgeCommand {
        public final ActorRef<ProductsResponse> replyTo;

        public GetProductsCommand(ActorRef<ProductsResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class GetOrderHistoryCommand implements FridgeCommand {
        public final ActorRef<OrderHistoryResponse> replyTo;

        public GetOrderHistoryCommand(ActorRef<OrderHistoryResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class OrderProductCommand implements FridgeCommand {
        public final Product product;
        public final int amount;
        public final ActorRef<OrderResponse> replyTo;

        public OrderProductCommand(Product product, int amount, ActorRef<OrderResponse> replyTo) {
            this.product = product;
            this.amount = amount;
            this.replyTo = replyTo;
        }
    }

    public static final class ProductsResponse {
        public final Map<Product, Integer> products;

        public ProductsResponse(Map<Product, Integer> products) {
            this.products = products;
        }
    }

    public static final class OrderHistoryResponse {
        public final List<Order> orderHistory;

        public OrderHistoryResponse(List<Order> orderHistory) {
            this.orderHistory = orderHistory;
        }
    }

    public static final class OrderResponse {
        public final boolean success;
        public final String message;
        public final Receipt receipt;

        public OrderResponse(boolean success, String message, Receipt receipt) {
            this.success = success;
            this.message = message;
            this.receipt = receipt;
        }
    }

    public static final class OperationResult {
        public final boolean success;
        public final String message;

        public OperationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private final Map<Product, Integer> products;
    private final List<Order> orderHistory;
    private final double maxWeight;
    private final int maxProducts;
    private double currentWeight;
    private final ActorRef<OrderProcessor.OrderCommand> orderProcessor;

    public static Behavior<FridgeCommand> create(int maxProducts, double maxWeight, ActorRef<OrderProcessor.OrderCommand> orderProcessor) {
        return Behaviors.setup(context -> new Fridge(context, maxProducts, maxWeight, orderProcessor));
    }

    private Fridge(ActorContext<FridgeCommand> context, int maxProducts, double maxWeight, ActorRef<OrderProcessor.OrderCommand> orderProcessor) {
        super(context);
        this.maxProducts = maxProducts;
        this.maxWeight = maxWeight;
        this.orderProcessor = orderProcessor;
        this.products = new HashMap<>();
        this.orderHistory = new ArrayList<>();
        this.currentWeight = 0.0;

        getContext().getLog().info("[DEVICE] Fridge started with capacity: {} products, {} kg", maxProducts, maxWeight);
    }

    @Override
    public Receive<FridgeCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(AddProductCommand.class, this::onAddProduct)
                .onMessage(ConsumeProductCommand.class, this::onConsumeProduct)
                .onMessage(GetProductsCommand.class, this::onGetProducts)
                .onMessage(GetOrderHistoryCommand.class, this::onGetOrderHistory)
                .onMessage(OrderProductCommand.class, this::onOrderProduct)
                .build();
    }

    private Behavior<FridgeCommand> onAddProduct(AddProductCommand cmd) {
        int currentTotal = products.values().stream().mapToInt(Integer::intValue).sum();

        if (currentTotal + cmd.amount > maxProducts) {
            cmd.replyTo.tell(new OperationResult(false, "Exceeds maximum product capacity"));
            return this;
        }

        double newWeight = currentWeight + (cmd.product.weight() * cmd.amount);
        if (newWeight > maxWeight) {
            cmd.replyTo.tell(new OperationResult(false, "Exceeds maximum weight capacity"));
            return this;
        }

        products.merge(cmd.product, cmd.amount, Integer::sum);
        currentWeight = newWeight;

        getContext().getLog().info("[DEVICE] Added {} x {} to fridge", cmd.amount, cmd.product.name());
        cmd.replyTo.tell(new OperationResult(true, "Products added successfully"));

        return this;
    }

    private Behavior<FridgeCommand> onConsumeProduct(ConsumeProductCommand cmd) {
        Optional<Product> productOpt = products.keySet().stream()
                .filter(p -> p.name().equals(cmd.productName))
                .findFirst();

        if (productOpt.isEmpty()) {
            cmd.replyTo.tell(new OperationResult(false, "Product not found"));
            return this;
        }

        Product product = productOpt.get();
        int currentAmount = products.getOrDefault(product, 0);

        if (currentAmount < cmd.amount) {
            cmd.replyTo.tell(new OperationResult(false, "Not enough products available"));
            return this;
        }

        int newAmount = currentAmount - cmd.amount;
        if (newAmount > 0) {
            products.put(product, newAmount);
        } else {
            products.remove(product);

            getContext().getSelf().tell(new OrderProductCommand(product, 1, getContext().messageAdapter(
                    OrderResponse.class,
                    response -> new AddProductCommand(product, 1, getContext().messageAdapter(
                            OperationResult.class,
                            result -> null  // Ignoriere die Antwort
                    ))
            )));
        }

        currentWeight -= product.weight() * cmd.amount;
        getContext().getLog().info("[DEVICE] Consumed {} x {}", cmd.amount, product.name());
        cmd.replyTo.tell(new OperationResult(true, "Products consumed"));

        return this;
    }

    private Behavior<FridgeCommand> onGetProducts(GetProductsCommand cmd) {
        cmd.replyTo.tell(new ProductsResponse(new HashMap<>(products)));
        return this;
    }

    private Behavior<FridgeCommand> onGetOrderHistory(GetOrderHistoryCommand cmd) {
        cmd.replyTo.tell(new OrderHistoryResponse(new ArrayList<>(orderHistory)));
        return this;
    }

    private Behavior<FridgeCommand> onOrderProduct(OrderProductCommand cmd) {
        getContext().spawnAnonymous(
                OrderSession.create(cmd.product, cmd.amount, maxProducts, maxWeight,
                        currentWeight, products, orderProcessor, orderHistory, cmd.replyTo)
        );
        return this;
    }
}