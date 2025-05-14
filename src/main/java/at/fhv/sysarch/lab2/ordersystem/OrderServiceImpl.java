package at.fhv.sysarch.lab2.ordersystem;

import akka.actor.typed.ActorRef;
import at.fhv.sysarch.lab2.homeautomation.Fridge.Product;
import at.fhv.sysarch.lab2.homeautomation.grpc.OrderRequest;
import at.fhv.sysarch.lab2.homeautomation.grpc.OrderResponse;
import at.fhv.sysarch.lab2.homeautomation.grpc.OrderService;
import at.fhv.sysarch.lab2.homeautomation.grpc.ProductInfo;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class OrderServiceImpl implements OrderService {

    private final ActorRef<OrderProcessor.OrderCommand> orderProcessor;

    public OrderServiceImpl(ActorRef<OrderProcessor.OrderCommand> orderProcessor) {
        this.orderProcessor = orderProcessor;
    }

    @Override
    public CompletionStage<OrderResponse> processOrder(OrderRequest request) {
        Product product =
                new at.fhv.sysarch.lab2.homeautomation.Fridge.Product(
                        request.getProduct().getName(),
                        request.getProduct().getPrice(),
                        request.getProduct().getWeight()
                );

        CompletableFuture<at.fhv.sysarch.lab2.homeautomation.Fridge.Receipt> future =
                new CompletableFuture<>();

        orderProcessor.tell(
                new OrderProcessor.ProcessOrderCommand(
                        product,
                        request.getAmount(),
                        receipt -> {
                            future.complete(receipt);
                        }
                )
        );

        future.thenAccept(receipt -> {
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
        });
        return null;
    }

}