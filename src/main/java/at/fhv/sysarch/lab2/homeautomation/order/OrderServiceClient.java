package at.fhv.sysarch.lab2.homeautomation.order;


import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.Fridge.Product;
import at.fhv.sysarch.lab2.homeautomation.Fridge.Receipt;
import at.fhv.sysarch.lab2.homeautomation.grpc.OrderRequest;
import at.fhv.sysarch.lab2.homeautomation.grpc.OrderResponse;
import at.fhv.sysarch.lab2.homeautomation.grpc.ProductInfo;
import at.fhv.sysarch.lab2.ordersystem.OrderServiceImpl;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OrderServiceClient extends AbstractBehavior<OrderServiceClient.OrderClientCommand> {

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

    private final ManagedChannel channel;
    //private final OrderServiceGrpc.OrderServiceBlockingStub blockingStub;

    public static Behavior<OrderClientCommand> create(String host, int port) {
        return Behaviors.setup(context -> new OrderServiceClient(context, host, port));
    }

    private OrderServiceClient(ActorContext<OrderClientCommand> context, String host, int port) {
        super(context);
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        //this.blockingStub = OrderServiceImpl.newBlockingStub(channel);
        getContext().getLog().info("OrderServiceClient connected to {}:{}", host, port);

        context.getSystem().getWhenTerminated().thenAccept(done -> {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                getContext().getLog().error("Error shutting down gRPC channel", e);
            }
        });
    }

    @Override
    public Receive<OrderClientCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessOrderCommand.class, this::onProcessOrder)
                .build();
    }

    private Behavior<OrderClientCommand> onProcessOrder(ProcessOrderCommand cmd) {
        try {
            ProductInfo productInfo = ProductInfo.newBuilder()
                    .setName(cmd.product.name())
                    .setPrice(cmd.product.price())
                    .setWeight(cmd.product.weight())
                    .build();

            OrderRequest request = OrderRequest.newBuilder()
                    .setProduct(productInfo)
                    .setAmount(cmd.amount)
                    .build();

            OrderResponse response = blockingStub.processOrder(request);

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

        } catch (Exception e) {
            getContext().getLog().error("Error processing order via gRPC", e);
        }

        return this;
    }
}