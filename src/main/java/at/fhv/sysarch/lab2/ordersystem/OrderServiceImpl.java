package at.fhv.sysarch.lab2.ordersystem;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import at.fhv.sysarch.lab2.homeautomation.Fridge.Product;
import at.fhv.sysarch.lab2.homeautomation.Fridge.Receipt;
import at.fhv.sysarch.lab2.homeautomation.grpc.OrderRequest;
import at.fhv.sysarch.lab2.homeautomation.grpc.OrderResponse;
import at.fhv.sysarch.lab2.homeautomation.grpc.OrderService;
import at.fhv.sysarch.lab2.homeautomation.grpc.ProductInfo;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletionStage;

public class OrderServiceImpl implements OrderService {

    private final ActorRef<OrderProcessor.OrderCommand> orderProcessor;
    private final ActorSystem<Void> system;

    public OrderServiceImpl(ActorRef<OrderProcessor.OrderCommand> orderProcessor, ActorSystem<Void> system) {
        this.orderProcessor = orderProcessor;
        this.system = system;
    }

    @Override
    public CompletionStage<OrderResponse> processOrder(OrderRequest request) {
        Product product = new Product(
                request.getProduct().getName(),
                request.getProduct().getPrice(),
                request.getProduct().getWeight()
        );

        CompletionStage<Receipt> receiptFuture = AskPattern.ask(
                orderProcessor,
                replyTo -> new OrderProcessor.ProcessOrderCommand(
                        product,
                        request.getAmount(),
                        replyTo
                ),
                Duration.ofSeconds(3),
                system.scheduler()
        );

        return receiptFuture.thenApply(receipt -> {
            OrderResponse.Builder responseBuilder = OrderResponse.newBuilder()
                    .setOrderId(receipt.orderId())
                    .setTotalPrice(receipt.totalPrice())
                    .setTimestamp(receipt.timestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            for (int i = 0; i < receipt.items().size(); i++) {
                Product item = receipt.items().get(i);
                ProductInfo productInfo = ProductInfo.newBuilder()
                        .setName(item.name())
                        .setPrice(item.price())
                        .setWeight(item.weight())
                        .build();

                responseBuilder.addItems(productInfo);
                responseBuilder.addQuantities(receipt.quantities()[i]);
            }

            return responseBuilder.build();
        });
    }
}
