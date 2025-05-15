package at.fhv.sysarch.lab2.homeautomation.order;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.grpc.GrpcClientSettings;
import at.fhv.sysarch.lab2.homeautomation.Fridge.Product;
import at.fhv.sysarch.lab2.homeautomation.Fridge.Receipt;
import at.fhv.sysarch.lab2.homeautomation.grpc.*;
import akka.stream.Materializer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class OrderServiceClientImpl extends AbstractBehavior<OrderServiceClientImpl.OrderClientCommand> {

    public interface OrderClientCommand {}

    public static final class ProcessOrderCommand implements OrderClientCommand {
        public final Product product;
        public final int amount;
        public final ActorRef<Receipt> replyTo;

        public ProcessOrderCommand(Product product, int amount, ActorRef<Receipt> replyTo) {
            this.product = product;
            this.amount = amount;
            this.replyTo = replyTo;
        }
    }

    private final OrderService client;
    private final Materializer materializer;

    public static Behavior<OrderClientCommand> create(String host, int port) {
        return Behaviors.setup(context -> new OrderServiceClientImpl(context, host, port));
    }

    private OrderServiceClientImpl(ActorContext<OrderClientCommand> context, String host, int port) {
        super(context);

        GrpcClientSettings settings = GrpcClientSettings.connectToServiceAt(host, port, context.getSystem())
                .withTls(false);

        this.client = OrderServiceClient.create(settings, context.getSystem());
        this.materializer = Materializer.matFromSystem(context.getSystem());

        getContext().getLog().info("OrderServiceClient connected to {}:{}", host, port);
    }

    @Override
    public Receive<OrderClientCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessOrderCommand.class, this::onProcessOrder)
                .build();
    }

    private Behavior<OrderClientCommand> onProcessOrder(ProcessOrderCommand cmd) {
        ProductInfo productInfo = ProductInfo.newBuilder()
                .setName(cmd.product.name())
                .setPrice(cmd.product.price())
                .setWeight(cmd.product.weight())
                .build();

        OrderRequest request = OrderRequest.newBuilder()
                .setProduct(productInfo)
                .setAmount(cmd.amount)
                .build();

        CompletionStage<OrderResponse> responseFuture = client.processOrder(request);

        responseFuture.whenComplete((response, throwable) -> {
            if (throwable != null) {
                getContext().getLog().error("Error processing order via Akka gRPC", throwable);
                return;
            }

            List<Product> items = new ArrayList<>();
            int[] quantities = new int[response.getQuantitiesCount()];

            for (int i = 0; i < response.getItemsCount(); i++) {
                ProductInfo item = response.getItems(i);
                items.add(new Product(item.getName(), item.getPrice(), item.getWeight()));
                quantities[i] = response.getQuantities(i);
            }

            LocalDateTime timestamp = LocalDateTime.parse(
                    response.getTimestamp(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            Receipt receipt = new Receipt(
                    response.getOrderId(),
                    items,
                    quantities,
                    response.getTotalPrice(),
                    timestamp
            );

            cmd.replyTo.tell(receipt);
        });

        return this;
    }
}
